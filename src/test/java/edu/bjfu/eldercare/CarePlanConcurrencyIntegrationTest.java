package edu.bjfu.eldercare;

import static edu.bjfu.eldercare.WorkflowIntegrationTest.u;
import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.*;

import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "app.jobs=false")
class CarePlanConcurrencyIntegrationTest {
    @Autowired CareService service;
    @Autowired Store db;
    @Autowired PlatformTransactionManager transactions;

    record PlanCase(long elder, long assessment, long plan, LocalDateTime visit) {}

    private long create(String action, Map<String, Object> fields) {
        return ((Number) service.execute(u(1, "ADMIN"), action, fields)).longValue();
    }

    private PlanCase prepareCarePlan() {
        long elder =
                create(
                        "elder.create",
                        Map.ofEntries(
                                entry("name", "顾文秀"),
                                        entry("identity", UUID.randomUUID().toString()),
                                entry("birthDate", "1944-06-20"), entry("address", "青禾社区二号楼"),
                                entry("x", 10000), entry("y", 0)));
        var visit = LocalDateTime.now().plusDays(2).withNano(0);
        long assessment =
                create(
                        "assessment.create",
                        Map.ofEntries(
                                entry("elderId", elder),
                                entry("scale", "能力评估"),
                                entry("score", 32),
                                entry("level", "SEVERE"),
                                entry("validUntil", visit.plusMonths(4).toString())));
        long plan =
                create(
                        "plan.create",
                        Map.ofEntries(
                                entry("elderId", elder),
                                entry("assessmentId", assessment),
                                entry("bundleId", 1),
                                entry("caregiverId", 1),
                                entry("startsAt", visit.minusDays(1).toString()),
                                entry("endsAt", visit.plusMonths(2).toString())));
        return new PlanCase(elder, assessment, plan, visit);
    }

    @Test
    void bookingAlreadyInProgressPreventsConcurrentPlanPause() throws Exception {
        PlanCase example = prepareCarePlan();
        var admin = u(1, "ADMIN");
        var booking =
                Map.<String, Object>of(
                        "elderId",
                        example.elder(),
                        "itemId",
                        1,
                        "startsAt",
                        example.visit().toString(),
                        "channel",
                        "COMMUNITY");
        var pauseRequest =
                Map.<String, Object>of(
                        "planId", example.plan(), "status", "PAUSED", "reason", "家属希望暂停");
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> pause =
                    new TransactionTemplate(transactions)
                            .execute(
                                    tx -> {
                                        service.execute(admin, "order.create", booking);
                                        Future<?> pending =
                                                pool.submit(
                                                        () ->
                                                                service.execute(
                                                                        admin,
                                                                        "plan.state",
                                                                        pauseRequest));
                                        waitForPlanOperation(pending, "%for update%");
                                        return pending;
                                    });
            var error =
                    assertThrows(ExecutionException.class, () -> pause.get(6, TimeUnit.SECONDS));
            assertInstanceOf(Rule.class, error.getCause());
            assertTrue(error.getCause().getMessage().contains("未完成订单"));
            assertEquals(
                    "ACTIVE",
                    db.one("SELECT status FROM care_plan WHERE id=?", example.plan())
                            .get("status"));
            assertEquals(
                    1,
                    db.count(
                            "SELECT COUNT(*) FROM service_order WHERE elder_id=? AND"
                                    + " status='PENDING'",
                            example.elder()));
            assertEquals(
                    0,
                    db.count(
                            "SELECT COUNT(*) FROM audit_log WHERE table_name='care_plan' AND"
                                    + " row_id=? AND operation='STATE_REASON'",
                            example.plan()));
        } finally {
            pool.shutdownNow();
        }
    }

    // The contender can read the plan or the elder first. Release the booking after MySQL
    // sees the competing locking read or the operation has already finished.
    private void waitForPlanOperation(Future<?> pending, String lockingStatement) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String lockingRead =
                "SELECT COUNT(*) FROM information_schema.processlist WHERE db=DATABASE()"
                        + " AND id<>CONNECTION_ID() AND LOWER(info) LIKE ?";
        while (!pending.isDone() && db.count(lockingRead, lockingStatement) == 0) {
            if (System.nanoTime() >= deadline) fail("计划操作未到达预期的事务边界");
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    @Test
    void revisingAndResumingAPausedPlanUseTheSameLockOrder() throws Exception {
        PlanCase example = prepareCarePlan();
        var admin = u(1, "ADMIN");
        service.execute(
                admin,
                "plan.state",
                Map.of("planId", example.plan(), "status", "PAUSED", "reason", "调整照护安排"));
        var resume =
                Map.<String, Object>of(
                        "planId", example.plan(), "status", "ACTIVE", "reason", "准备恢复服务");
        var revision =
                Map.<String, Object>of(
                        "elderId",
                        example.elder(),
                        "planId",
                        example.plan(),
                        "assessmentId",
                        example.assessment(),
                        "bundleId",
                        1,
                        "caregiverId",
                        1,
                        "startsAt",
                        example.visit().toString(),
                        "endsAt",
                        example.visit().plusMonths(1).toString(),
                        "reason",
                        "按新服务安排修订");
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> second =
                    new TransactionTemplate(transactions)
                            .execute(
                                    tx -> {
                                        // Pause the revision at the elder lock it normally takes
                                        // before reading the old plan.
                                        db.one(
                                                "SELECT id FROM elder WHERE id=? FOR UPDATE",
                                                example.elder());
                                        Future<?> pending =
                                                pool.submit(
                                                        () ->
                                                                service.execute(
                                                                        admin,
                                                                        "plan.state",
                                                                        resume));
                                        waitForPlanOperation(
                                                pending, "%from elder where id=%for update%");
                                        service.execute(admin, "plan.revise", revision);
                                        return pending;
                                    });
            var failed =
                    assertThrows(ExecutionException.class, () -> second.get(6, TimeUnit.SECONDS));
            assertInstanceOf(Rule.class, failed.getCause());
            assertEquals(
                    "ENDED",
                    db.one("SELECT status FROM care_plan WHERE id=?", example.plan())
                            .get("status"));
            assertEquals(
                    1,
                    db.count(
                            "SELECT COUNT(*) FROM v_plan_context WHERE elder_id=? AND"
                                    + " status='ACTIVE'",
                            example.elder()));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Transactional
    void choosingTheCurrentCommunityCannotInterruptCare() {
        PlanCase example = prepareCarePlan();
        var admin = u(1, "ADMIN");
        var transfer =
                Map.<String, Object>of(
                        "elderId",
                        example.elder(),
                        "communityId",
                        1,
                        "x",
                        10000,
                        "y",
                        10,
                        "address",
                        "同社区三号楼");
        var error =
                assertThrows(Rule.class, () -> service.execute(admin, "elder.transfer", transfer));
        assertTrue(error.getMessage().contains("更新老人住址"));
        assertEquals(
                "ACTIVE",
                db.one("SELECT status FROM care_plan WHERE id=?", example.plan()).get("status"));
        assertEquals(
                "青禾社区二号楼",
                db.one("SELECT address FROM elder WHERE id=?", example.elder()).get("address"));
        service.execute(admin, "elder.update", transfer);
        assertEquals(
                "同社区三号楼",
                db.one("SELECT address FROM elder WHERE id=?", example.elder()).get("address"));
        assertEquals(
                "ACTIVE",
                db.one("SELECT status FROM care_plan WHERE id=?", example.plan()).get("status"));
    }

    @Test
    @Transactional
    void transferredFileUsesItsNewCommunityForAddressAndPlanChanges() {
        PlanCase example = prepareCarePlan();
        var original = u(1, "ADMIN");
        var destination = u(2, "ADMIN");
        long contact =
                ((Number)
                                service.execute(
                                        original,
                                        "guardian.create",
                                        Map.of(
                                                "elderId",
                                                example.elder(),
                                                "accountId",
                                                u(1, "FAMILY").id(),
                                                "relation",
                                                "女儿",
                                                "phone",
                                                "课程联系号码")))
                        .longValue();
        service.execute(
                original,
                "elder.transfer",
                Map.of(
                        "elderId",
                        example.elder(),
                        "communityId",
                        2,
                        "x",
                        20000,
                        "y",
                        0,
                        "address",
                        "迁入地址"));
        assertEquals(
                "PAUSED",
                db.one("SELECT status FROM care_plan WHERE id=?", example.plan()).get("status"));
        var address =
                Map.<String, Object>of(
                        "elderId", example.elder(), "x", 20000, "y", 20, "address", "新社区三号楼");
        assertThrows(Forbidden.class, () -> service.execute(original, "elder.update", address));
        var end =
                Map.<String, Object>of(
                        "planId", example.plan(), "status", "ENDED", "reason", "迁入社区重新安排");
        assertThrows(Forbidden.class, () -> service.execute(original, "plan.state", end));
        var tag =
                Map.<String, Object>of("elderId", example.elder(), "tag", "ALONE", "enabled", true);
        var guardian =
                Map.<String, Object>of(
                        "guardianId", contact, "relation", "女儿", "phone", "课程更新号码", "active", true);
        assertThrows(Forbidden.class, () -> service.execute(original, "elder.tags", tag));
        assertThrows(Forbidden.class, () -> service.execute(original, "guardian.update", guardian));
        service.execute(destination, "elder.tags", tag);
        service.execute(destination, "guardian.update", guardian);
        assertEquals(
                1,
                db.count(
                        "SELECT COUNT(*) FROM elder_tag WHERE elder_id=? AND tag='ALONE'",
                        example.elder()));
        service.execute(destination, "elder.update", address);
        service.execute(destination, "plan.state", end);
        assertEquals(
                "新社区三号楼",
                db.one("SELECT address FROM elder WHERE id=?", example.elder()).get("address"));
        assertEquals(
                "ENDED",
                db.one("SELECT status FROM care_plan WHERE id=?", example.plan()).get("status"));
    }
}
