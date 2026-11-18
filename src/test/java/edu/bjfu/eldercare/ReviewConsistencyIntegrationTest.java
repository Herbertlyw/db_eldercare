package edu.bjfu.eldercare;

import static edu.bjfu.eldercare.WorkflowIntegrationTest.u;
import static org.junit.jupiter.api.Assertions.*;

import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.ReadService;
import edu.bjfu.eldercare.application.port.query.ListFilter;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "app.jobs=false")
class ReviewConsistencyIntegrationTest {
    @Autowired CareService service;
    @Autowired ReadService reads;
    @Autowired Store db;
    @Autowired PlatformTransactionManager tm;

    // Keep the first write uncommitted until MySQL sees the second connection requesting the row
    // lock.
    void whileLocked(String table, Runnable winner, Runnable contender) throws Exception {
        var pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> rejected =
                    new TransactionTemplate(tm)
                            .execute(
                                    tx -> {
                                        winner.run();
                                        var attempt =
                                                pool.submit(
                                                        () ->
                                                                assertThrows(
                                                                        Rule.class,
                                                                        contender::run));
                                        long until =
                                                System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                                        while (db.count(
                                                        "SELECT COUNT(*) FROM"
                                                            + " information_schema.processlist"
                                                            + " WHERE db=DATABASE() AND"
                                                            + " id<>CONNECTION_ID() AND LOWER(info)"
                                                            + " LIKE ?",
                                                        "%from " + table + " where id=%for update%")
                                                == 0) {
                                            if (System.nanoTime() >= until || attempt.isDone())
                                                fail("第二个操作未进入数据库行锁等待");
                                            java.util.concurrent.locks.LockSupport.parkNanos(
                                                    TimeUnit.MILLISECONDS.toNanos(10));
                                        }
                                        return attempt;
                                    });
            rejected.get(8, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void checkinWinsAgainstWaitingReleaseWithoutLosingCapacity() throws Exception {
        long order =
                db.insert(
                        "INSERT INTO"
                            + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x,position_y)"
                            + " SELECT elder_id,item_id,community_id,starts_at + INTERVAL 1"
                            + " DAY,ends_at + INTERVAL 1"
                            + " DAY,address,channel,amount,created_by,position_x,position_y FROM"
                            + " service_order WHERE id=38");
        var worker = u(19, "CAREGIVER");
        long shift =
                db.insert(
                        "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity) SELECT"
                            + " caregiver_id,starts_at + INTERVAL 1 DAY,ends_at + INTERVAL 1 DAY,1"
                            + " FROM shift WHERE id=38");
        service.execute(
                u(19, "ADMIN"),
                "order.assign",
                Map.of("orderId", order, "shiftId", shift, "reason", "并发签到"));
        service.execute(worker, "order.accept", Map.of("orderId", order));
        whileLocked(
                "service_order",
                () ->
                        service.execute(
                                worker,
                                "order.checkin",
                                Map.of("orderId", order, "x", 0, "y", 0, "location", "上门")),
                () ->
                        service.execute(
                                u(19, "MANAGER"),
                                "order.release",
                                Map.of("orderId", order, "reason", "调度")));
        assertEquals(
                "SERVING",
                db.one("SELECT status FROM service_order WHERE id=?", order).get("status"));
        assertEquals(
                1,
                db.count(
                        "SELECT COUNT(*) FROM attendance a JOIN dispatch d ON"
                                + " a.dispatch_id=d.id WHERE d.order_id=? AND d.status='ACCEPTED'",
                        order));
        assertEquals(1, db.count("SELECT used_count FROM shift WHERE id=?", shift));
        service.execute(
                worker, "order.finish", Map.of("orderId", order, "x", 0, "y", 0, "notes", "服务记录"));
    }

    @Test
    void contradictoryReviewsCannotOverwriteApprovedMaterial() throws Exception {
        long id =
                db.insert(
                        "INSERT INTO"
                            + " inspection(caregiver_id,inspector_id,method,score,issue,rectification,status)"
                            + " VALUES(1,6,'PHONE',50,'抽查问题','提交材料','OPEN')");
        long material =
                ((Number)
                                service.execute(
                                        u(1, "MANAGER"),
                                        "rectification.submit",
                                        Map.of(
                                                "inspectionId",
                                                id,
                                                "measures",
                                                "已落实",
                                                "evidence",
                                                "附件")))
                        .longValue();
        whileLocked(
                "inspection",
                () ->
                        service.execute(
                                u(1, "REGULATOR"),
                                "inspection.close",
                                Map.of("inspectionId", id, "approve", true, "result", "通过")),
                () ->
                        service.execute(
                                u(2, "REGULATOR"),
                                "inspection.close",
                                Map.of("inspectionId", id, "approve", false, "result", "退回")));
        var detail =
                db.one(
                        "SELECT i.status,i.result,r.decision,r.review_note,r.reviewer_id FROM"
                                + " inspection i JOIN rectification_submission r ON"
                                + " r.inspection_id=i.id WHERE r.id=?",
                        material);
        assertEquals("CLOSED", detail.get("status"));
        assertEquals("ACCEPT", detail.get("decision"));
        assertEquals("通过", detail.get("result"));
        assertEquals(detail.get("result"), detail.get("review_note"));
        assertEquals(6, ((Number) detail.get("reviewer_id")).longValue());
    }

    @Test
    @Transactional
    void missingMaterialLeavesReviewOpen() {
        long id =
                db.insert(
                        "INSERT INTO"
                            + " inspection(caregiver_id,inspector_id,method,score,issue,rectification,status)"
                            + " VALUES(1,6,'PHONE',50,'抽查问题','提交材料','REVIEW')");
        assertThrows(
                Rule.class,
                () ->
                        service.execute(
                                u(1, "REGULATOR"),
                                "inspection.close",
                                Map.of("inspectionId", id, "approve", true, "result", "通过")));
        assertEquals(
                "REVIEW", db.one("SELECT status FROM inspection WHERE id=?", id).get("status"));
    }

    @Test
    @Transactional
    void missedOrderStaysPendingUntilContactAndCanBeMovedToAnEligibleSlot() {
        db.update(
                "UPDATE service_order SET starts_at=NOW()-INTERVAL 2 HOUR,ends_at=NOW()-INTERVAL 1"
                        + " HOUR,created_at=NOW()-INTERVAL 3 HOUR WHERE id=2");
        var admin = u(1, "ADMIN");
        var byId = new ListFilter("", "", null, null, "id", 2L);
        var pool = (Map<?, ?>) reads.list(admin, "dispatchPool", 0, 1, byId);
        assertEquals(0, ((Number) pool.get("total")).longValue());
        assertThrows(
                Rule.class, () -> service.execute(admin, "order.recommend", Map.of("orderId", 2)));
        assertThrows(
                Rule.class,
                () ->
                        service.execute(
                                admin,
                                "order.assign",
                                Map.of("orderId", 2, "shiftId", 2, "reason", "派单")));
        assertThrows(
                org.springframework.dao.DataAccessException.class,
                () -> db.call("assign_order", 2, 2, "派单"));
        assertEquals(0, db.count("SELECT used_count FROM shift WHERE id=2"));
        assertEquals(
                "PENDING", db.one("SELECT status FROM service_order WHERE id=2").get("status"));
        var request =
                Map.<String, Object>of(
                        "orderId",
                        2,
                        "startsAt",
                        LocalDate.now().plusDays(1).atTime(10, 0).toString());
        assertThrows(
                Forbidden.class, () -> service.execute(u(2, "ADMIN"), "order.reschedule", request));
        var rejected =
                assertThrows(
                        org.springframework.dao.DataAccessException.class,
                        () ->
                                service.execute(
                                        admin,
                                        "order.reschedule",
                                        Map.of(
                                                "orderId",
                                                2,
                                                "startsAt",
                                                LocalDateTime.now().plusYears(5).toString())));
        assertTrue(rejected.getMostSpecificCause().getMessage().contains("ORDER_PLAN_INVALID"));
        service.execute(admin, "order.reschedule", request);
        var available = (Map<?, ?>) reads.list(admin, "orders", 0, 1, byId);
        var rows = (java.util.List<?>) available.get("rows");
        assertEquals(1, ((Number) ((Map<?, ?>) rows.get(0)).get("dispatch_window")).intValue());
        assertEquals(
                1,
                db.count(
                        "SELECT COUNT(*) FROM service_order WHERE id=2 AND status='PENDING' AND"
                            + " starts_at>NOW() AND TIMESTAMPDIFF(MINUTE,starts_at,ends_at)=60"));
        assertThrows(Rule.class, () -> service.execute(admin, "order.reschedule", request));
        service.execute(
                admin, "order.assign", Map.of("orderId", 2, "shiftId", 2, "reason", "确认新时段"));
    }

    @Test
    @Transactional
    void hourAggregateCoversAllFilteredRowsIncludingOtherPagesAndRespectsScope() {
        for (int i = 0; i < 106; i++) {
            long dispatch =
                    db.insert(
                            "INSERT INTO dispatch(order_id,shift_id,status)"
                                    + " VALUES(1,1,'FINISHED')");
            String state =
                    i < 102 ? "VALID" : i < 104 ? "REVIEW" : i == 104 ? "PENDING" : "INVALID";
            db.update(
                    "INSERT INTO"
                        + " attendance(dispatch_id,check_in,location_note,fence_ok,status,effective_minutes)"
                        + " VALUES(?,'2001-05-02','工时校验',1,?,5)",
                    dispatch,
                    state);
        }
        var date = LocalDate.of(2001, 5, 2);
        for (String role : java.util.List.of("ADMIN", "CAREGIVER", "MANAGER")) {
            var filter = new ListFilter("", "", date, date, "", null);
            for (int page : java.util.List.of(0, 10)) {
                var result = (Map<?, ?>) reads.list(u(1, role), "hours", page, 10, filter);
                assertEquals(106, ((Number) result.get("total")).longValue());
                var stats = (Map<?, ?>) result.get("summary");
                assertEquals(510, ((Number) stats.get("minutes")).longValue());
                assertEquals(102, ((Number) stats.get("valid")).longValue());
                assertEquals(3, ((Number) stats.get("pending")).longValue());
                assertEquals(1, ((Number) stats.get("invalid")).longValue());
            }
            var other = (Map<?, ?>) reads.list(u(2, role), "hours", 0, 1, filter);
            assertEquals(
                    0, ((Number) ((Map<?, ?>) other.get("summary")).get("minutes")).longValue());
            var invalid =
                    (Map<?, ?>)
                            reads.list(
                                    u(1, role),
                                    "hours",
                                    0,
                                    1,
                                    new ListFilter("", "INVALID", date, date, "", null));
            assertEquals(
                    0, ((Number) ((Map<?, ?>) invalid.get("summary")).get("minutes")).longValue());
        }
    }
}
