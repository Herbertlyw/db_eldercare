package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.application.security.Unauthorized;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"app.jobs=false"})
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseIntegrationTest {

    @Autowired Store d;

    @Autowired CareService service;

    @Autowired Auth auth;

    @Autowired MockMvc mvc;

    @Autowired PlatformTransactionManager tm;

    Auth.User admin = new Auth.User(1, "admin1", "ADMIN", 1, 1L);

    Auth.User family = new Auth.User(2, "family1", "FAMILY", 1, 1L);

    void rollback(Runnable r) {
        new TransactionTemplate(tm)
                .execute(
                        s -> {
                            s.setRollbackOnly();
                            r.run();
                            return null;
                        });
    }

    void expiredAssessmentClock(Runnable work) {
        long future = d.count("SELECT UNIX_TIMESTAMP(valid_until)+1 FROM assessment WHERE id=1");
        d.update("SET timestamp=?", future);
        try {
            work.run();
        } finally {
            d.update("SET timestamp=0");
        }
    }

    void reject(Runnable r) {
        assertThrows(
                RuntimeException.class,
                () ->
                        new TransactionTemplate(tm)
                                .execute(
                                        s -> {
                                            r.run();
                                            return null;
                                        }));
    }

    @Test
    @Order(1)
    void everyTableHasTwentyRows() {
        for (var t : d.rows("SHOW FULL TABLES WHERE Table_type='BASE TABLE'")) {
            String name = t.values().iterator().next().toString();
            assertTrue(d.count("SELECT COUNT(*) FROM `" + name + "`") >= 20, name);
        }
    }

    @Test
    void fifteenViewsExecute() {
        var views = d.rows("SHOW FULL TABLES WHERE Table_type='VIEW'");
        assertTrue(views.size() >= 15);
        for (var v : views) {
            d.rows("SELECT * FROM " + v.values().iterator().next() + " LIMIT 3");
        }
    }

    @Test
    void fifteenQueryPairsAreEquivalent() throws Exception {
        var queries =
                new ObjectMapper().readTree(Files.readString(Path.of("database/queries.json")));
        assertEquals(15, queries.size());
        for (var q : queries) {
            var a = d.rows(q.get("a").asText());
            var b = d.rows(q.get("b").asText());
            assertEquals(canonical(a), canonical(b), q.get("id").asText());
        }
    }

    String canonical(List<Map<String, Object>> rows) {
        return rows.stream()
                .map(
                        m ->
                                m.values().stream()
                                        .map(x -> x == null ? "NULL" : x.toString())
                                        .toList()
                                        .toString())
                .sorted()
                .toList()
                .toString();
    }

    @Test
    void duplicateIdentityRejected() {
        reject(
                () ->
                        d.update(
                                "INSERT INTO"
                                    + " elder(name,identity_hash,identity_cipher,birth_date,address,community_id)"
                                    + " SELECT"
                                    + " name,identity_hash,identity_cipher,birth_date,address,community_id"
                                    + " FROM elder WHERE id=1"));
    }

    @Test
    void danglingForeignKeyRejected() {
        reject(() -> d.update("INSERT INTO elder_tag(elder_id,tag) VALUES(999999,'ALONE')"));
    }

    @Test
    void overlappingPlanRejected() {
        reject(
                () ->
                        d.update(
                                "INSERT INTO"
                                    + " care_plan(assessment_id,bundle_id,caregiver_id,starts_at,ends_at)"
                                    + " SELECT"
                                    + " assessment_id,bundle_id,caregiver_id,starts_at,ends_at FROM"
                                    + " v_plan_context WHERE id=1"));
    }

    @Test
    void assessmentCannotBelongToAnotherElder() {
        reject(
                () ->
                        service.execute(
                                admin,
                                "plan.create",
                                Map.of(
                                        "elderId",
                                        1,
                                        "assessmentId",
                                        2,
                                        "bundleId",
                                        1,
                                        "caregiverId",
                                        1,
                                        "startsAt",
                                        LocalDateTime.now().plusDays(100).toString(),
                                        "endsAt",
                                        LocalDateTime.now().plusDays(101).toString())));
    }

    @Test
    void overlappingShiftRejected() {
        reject(
                () ->
                        d.update(
                                "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity) SELECT"
                                    + " caregiver_id,starts_at,ends_at,capacity FROM shift WHERE"
                                    + " id=2"));
    }

    @Test
    void shiftCapacityCheck() {
        reject(() -> d.update("UPDATE shift SET used_count=capacity+1 WHERE id=2"));
    }

    @Test
    void confirmationRequired() {
        reject(() -> d.update("UPDATE service_order SET status='COMPLETED' WHERE id=2"));
    }

    @Test
    void validDispatchAndHistory() {
        rollback(
                () -> {
                    service.execute(
                            admin,
                            "order.assign",
                            Map.of("orderId", 2, "shiftId", 2, "reason", "首次测试"));
                    service.execute(
                            admin,
                            "order.assign",
                            Map.of("orderId", 2, "shiftId", 2, "reason", "重新安排时间"));
                    assertEquals(
                            2, d.count("SELECT COUNT(*) FROM v_dispatch_context WHERE order_id=2"));
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT COUNT(*) FROM v_dispatch_context WHERE order_id=2 AND"
                                            + " active_order IS NOT NULL"));
                    assertEquals(1, d.count("SELECT used_count FROM shift WHERE id=2"));
                });
    }

    @Test
    void wrongAreaOrCertificateRejected() {
        reject(
                () ->
                        service.execute(
                                admin,
                                "order.assign",
                                Map.of("orderId", 2, "shiftId", 4, "reason", "无资格护工")));
        assertEquals("PENDING", d.one("SELECT status FROM service_order WHERE id=2").get("status"));
    }

    @Test
    void expiredAssessmentStopsDispatch() {
        rollback(
                () -> {
                    expiredAssessmentClock(
                            () -> {
                                assertThrows(
                                        RuntimeException.class,
                                        () -> d.call("assign_order", 2, 2, "过期测试"));
                                d.call("expire_plans");
                                assertEquals(
                                        "PAUSED",
                                        d.one("SELECT status FROM v_plan_context WHERE id=1")
                                                .get("status"));
                            });
                });
    }

    @Test
    void expiredCredentialRejected() {
        rollback(
                () -> {
                    d.update(
                            "UPDATE credential SET valid_until=NOW()-INTERVAL 1 SECOND WHERE id=1");
                    assertThrows(
                            RuntimeException.class, () -> d.call("assign_order", 2, 2, "证书过期"));
                });
    }

    @Test
    void rectificationBlocksDispatch() {
        rollback(
                () -> {
                    d.update("UPDATE inspection SET status='OPEN' WHERE id=1");
                    assertThrows(
                            RuntimeException.class, () -> d.call("assign_order", 2, 2, "整改测试"));
                });
    }

    @Test
    void immutableCompletedOrder() {
        reject(() -> d.update("UPDATE service_order SET amount=0 WHERE id=1"));
    }

    @Test
    void historicalDeleteForbidden() {
        reject(() -> d.update("DELETE FROM attendance WHERE id=1"));
    }

    @Test
    void deviceRebindPreservesOldOwner() {
        rollback(
                () -> {
                    long next =
                            d.insert(
                                    "INSERT INTO"
                                        + " elder(name,identity_hash,identity_cipher,birth_date,address,community_id,position_x)"
                                        + " SELECT"
                                        + " name,UUID(),identity_cipher,birth_date,address,community_id,position_x"
                                        + " FROM elder WHERE id=1");
                    d.update("UPDATE device_binding SET unbound_at=NOW(6) WHERE id=1");
                    d.insert(
                            "INSERT INTO device_binding(device_id,elder_id,bound_at)"
                                    + " VALUES(1,?,NOW(6))",
                            next);
                    assertEquals(
                            next,
                            d.count(
                                    "SELECT elder_id FROM device_binding WHERE device_id=1 AND"
                                            + " unbound_at IS NULL"));
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT b.elder_id FROM vital v JOIN device_binding b ON"
                                            + " b.id=v.binding_id WHERE v.id=1"));
                });
    }

    @Test
    void sampleOutsideBindingRejected() {
        reject(
                () ->
                        d.update(
                                "INSERT INTO"
                                    + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                                    + " VALUES(1,1,'before-bind',NOW()-INTERVAL 365"
                                    + " DAY,50,'TEST')"));
    }

    @Test
    void duplicateSampleRejected() {
        reject(
                () ->
                        d.update(
                                "INSERT INTO"
                                    + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                                    + " SELECT"
                                    + " binding_id,metric_rule_id,sample_key,collected_at,metric_value,source"
                                    + " FROM vital WHERE id=1"));
    }

    @Test
    void thresholdCreatesPendingEvent() {
        rollback(
                () -> {
                    long n = d.count("SELECT COUNT(*) FROM emergency");
                    d.insert(
                            "INSERT INTO"
                                + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                                + " VALUES(1,1,'abnormal-test',NOW(),100,'TEST')");
                    assertEquals(n + 1, d.count("SELECT COUNT(*) FROM emergency"));
                });
    }

    @Test
    void escalationIsIdempotent() {
        rollback(
                () -> {
                    d.update("UPDATE emergency SET deadline=NOW()-INTERVAL 1 SECOND WHERE id=1");
                    d.call("escalate_events");
                    d.call("escalate_events");
                    assertEquals(2, d.count("SELECT level FROM emergency WHERE id=1"));
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT COUNT(*) FROM emergency_log WHERE event_id=1 AND"
                                            + " level=2"));
                });
    }

    @Test
    void claimInvalidOrderRollsBack() {
        long before = d.count("SELECT COUNT(*) FROM claim");
        reject(() -> d.call("submit_claim", 1, 1, "2099-01", "[2,999999]"));
        assertEquals(before, d.count("SELECT COUNT(*) FROM claim"));
    }

    @Test
    void duplicateClaimRejectedAcrossPolicy() {
        String month = d.one("SELECT period FROM claim WHERE id=1").get("period").toString();
        reject(() -> d.update("INSERT INTO claim(elder_id,item_id,period) VALUES(1,1,?)", month));
    }

    @Test
    void paymentIsImmutable() {
        reject(() -> d.update("UPDATE claim SET status='DRAFT' WHERE id=1"));
    }

    @Test
    void familyCannotReadOtherElder() {
        assertThrows(Forbidden.class, () -> service.elderScope(family, 2));
    }

    @Test
    void regulatorCannotCreateOrder() {
        assertThrows(
                Forbidden.class,
                () ->
                        service.execute(
                                new Auth.User(6, "regulator1", "REGULATOR", 1, 1L),
                                "order.create",
                                Map.of()));
    }

    @Test
    void sessionRequired() throws Exception {
        mvc.perform(get("/api/data/elders")).andExpect(status().isUnauthorized());
    }

    @Test
    void familyRowsAreScopedAndNoSensitiveFields() throws Exception {
        String token = (String) auth.login("family1", System.getenv("DEMO_PASSWORD")).get("token");
        String text =
                mvc.perform(get("/api/data/elders").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var body = new ObjectMapper().readTree(text);
        assertEquals(1, body.get("total").asInt());
        assertFalse(text.contains("identity_cipher"));
        assertFalse(text.contains("identity_hash"));
    }

    @Test
    void maliciousLoginDoesNotBypass() {
        assertThrows(Rule.class, () -> auth.login("' OR 1=1 --", "anything"));
    }

    @Test
    void logoutRevokesSession() {
        String token = (String) auth.login("family1", System.getenv("DEMO_PASSWORD")).get("token");
        auth.logout("Bearer " + token);
        assertThrows(Unauthorized.class, () -> auth.require("Bearer " + token));
    }

    @Test
    void serviceCompletionRequiresReviewAndConfirmation() {
        rollback(
                () -> {
                    service.execute(
                            admin,
                            "order.assign",
                            Map.of("orderId", 2, "shiftId", 2, "reason", "流程测试"));
                    var worker = new Auth.User(3, "caregiver1", "CAREGIVER", 1, 1L);
                    service.execute(worker, "order.accept", Map.of("orderId", 2));
                    service.execute(
                            worker,
                            "order.checkin",
                            Map.of("orderId", 2, "location", "模拟圈外", "x", 20000, "y", 0));
                    long dispatch =
                            d.count("SELECT id FROM v_dispatch_context WHERE active_order=2");
                    d.update(
                            "UPDATE attendance SET check_in=NOW()-INTERVAL 60 MINUTE WHERE"
                                    + " dispatch_id=?",
                            dispatch);
                    service.execute(
                            worker,
                            "order.finish",
                            Map.of("orderId", 2, "notes", "完整流程测试", "x", 20000, "y", 0));
                    long attendance =
                            d.count("SELECT id FROM attendance WHERE dispatch_id=?", dispatch);
                    assertEquals(
                            "REVIEW",
                            d.one("SELECT status FROM attendance WHERE id=?", attendance)
                                    .get("status"));
                    service.execute(
                            new Auth.User(4, "manager1", "MANAGER", 1, 1L),
                            "hours.review",
                            Map.of(
                                    "attendanceId",
                                    attendance,
                                    "approve",
                                    true,
                                    "reason",
                                    "核对演示服务证据"));
                    service.execute(
                            family,
                            "order.confirm",
                            Map.of("orderId", 2, "confirmation", "家属核实服务完成"));
                    assertEquals(
                            "COMPLETED",
                            d.one("SELECT status FROM service_order WHERE id=2").get("status"));
                    assertEquals(
                            60,
                            d.count(
                                    "SELECT effective_minutes FROM attendance WHERE id=?",
                                    attendance));
                });
    }

    @Test
    void mixedBatchUnauthorizedRollsBack() {
        long before = d.count("SELECT COUNT(*) FROM vital");
        reject(
                () ->
                        service.execute(
                                admin,
                                "vital.upload",
                                Map.of(
                                        "samples",
                                        List.of(
                                                Map.of(
                                                        "bindingId",
                                                        1,
                                                        "metricId",
                                                        1,
                                                        "sampleKey",
                                                        "batch-ok",
                                                        "collectedAt",
                                                        LocalDateTime.now().toString(),
                                                        "value",
                                                        50),
                                                Map.of(
                                                        "bindingId",
                                                        2,
                                                        "metricId",
                                                        2,
                                                        "sampleKey",
                                                        "batch-no-access",
                                                        "collectedAt",
                                                        LocalDateTime.now().toString(),
                                                        "value",
                                                        50)))));
        assertEquals(before, d.count("SELECT COUNT(*) FROM vital"));
    }

    @Test
    void loginFailureLockPersists() {
        for (int i = 0; i < 5; i++) {
            assertThrows(Rule.class, () -> auth.login("family20", "wrong-password"));
        }
        assertThrows(Rule.class, () -> auth.login("family20", System.getenv("DEMO_PASSWORD")));
        assertTrue(d.count("SELECT failures FROM account WHERE username='family20'") >= 5);
        d.update("UPDATE account SET failures=0,locked_until=NULL WHERE username='family20'");
    }

    @Test
    void unusedShiftCanBeDeleted() {
        rollback(
                () -> {
                    var manager = new Auth.User(4, "manager1", "MANAGER", 1, 1L);
                    long n =
                            ((Number)
                                            service.execute(
                                                    manager,
                                                    "shift.create",
                                                    Map.of(
                                                            "caregiverId",
                                                            1,
                                                            "startsAt",
                                                            LocalDateTime.now()
                                                                    .plusDays(50)
                                                                    .toString(),
                                                            "endsAt",
                                                            LocalDateTime.now()
                                                                    .plusDays(50)
                                                                    .plusHours(1)
                                                                    .toString(),
                                                            "capacity",
                                                            2)))
                                    .longValue();
                    service.execute(manager, "shift.delete", Map.of("shiftId", n));
                    assertEquals(0, d.count("SELECT COUNT(*) FROM shift WHERE id=?", n));
                });
    }

    // Regression cases found by checking the task sheet against actual state transitions.
    @Test
    void hourlyOrderPriceUsesDuration() {
        rollback(
                () -> {
                    d.update(
                            "UPDATE service_item SET unit='HOUR',price=80,duration_minutes=90 WHERE"
                                    + " id=1");
                    long order =
                            ((Number)
                                            service.execute(
                                                    admin,
                                                    "order.create",
                                                    Map.of(
                                                            "elderId",
                                                            1,
                                                            "itemId",
                                                            1,
                                                            "startsAt",
                                                            LocalDateTime.now()
                                                                    .plusDays(2)
                                                                    .toString())))
                                    .longValue();
                    assertEquals(
                            120, d.count("SELECT amount FROM service_order WHERE id=?", order));
                });
    }

    @Test
    void leaveAfterAssignmentPreventsAccept() {
        rollback(
                () -> {
                    service.execute(
                            admin,
                            "order.assign",
                            Map.of("orderId", 2, "shiftId", 2, "reason", "资格复查"));
                    d.update("UPDATE caregiver SET status='LEAVE' WHERE id=1");
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    service.execute(
                                            new Auth.User(3, "caregiver1", "CAREGIVER", 1, 1L),
                                            "order.accept",
                                            Map.of("orderId", 2)));
                });
    }

    @Test
    void newRectificationPreventsAccept() {
        rollback(
                () -> {
                    service.execute(
                            admin,
                            "order.assign",
                            Map.of("orderId", 2, "shiftId", 2, "reason", "整改复查"));
                    d.update("UPDATE inspection SET status='OPEN' WHERE id=1");
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    service.execute(
                                            new Auth.User(3, "caregiver1", "CAREGIVER", 1, 1L),
                                            "order.accept",
                                            Map.of("orderId", 2)));
                });
    }

    @Test
    void expiredAssessmentPreventsCheckin() {
        rollback(
                () -> {
                    var worker = new Auth.User(3, "caregiver1", "CAREGIVER", 1, 1L);
                    service.execute(
                            admin,
                            "order.assign",
                            Map.of("orderId", 2, "shiftId", 2, "reason", "签到复查"));
                    service.execute(worker, "order.accept", Map.of("orderId", 2));
                    expiredAssessmentClock(
                            () ->
                                    assertThrows(
                                            RuntimeException.class,
                                            () ->
                                                    service.execute(
                                                            worker,
                                                            "order.checkin",
                                                            Map.of(
                                                                    "orderId",
                                                                    2,
                                                                    "location",
                                                                    "模拟位置",
                                                                    "x",
                                                                    10000,
                                                                    "y",
                                                                    0))));
                });
    }

    @Test
    void alertImmediatelyNotifiesFamilyWorkerAndCommunity() {
        rollback(
                () -> {
                    long event =
                            ((Number)
                                            service.execute(
                                                    admin,
                                                    "event.create",
                                                    Map.of(
                                                            "elderId",
                                                            1,
                                                            "level",
                                                            2,
                                                            "source",
                                                            "MANUAL",
                                                            "description",
                                                            "课程模拟人工上报")))
                                    .longValue();
                    for (long account : new long[] {1, 2, 3}) {
                        assertEquals(
                                1,
                                d.count(
                                        "SELECT COUNT(*) FROM notification WHERE event_id=? AND"
                                                + " account_id=?",
                                        event,
                                        account));
                    }
                });
    }

    @Test
    void assignedButUnacceptedOrderEntersPool() {
        rollback(
                () -> {
                    service.execute(
                            admin,
                            "order.assign",
                            Map.of("orderId", 2, "shiftId", 2, "reason", "超时调度"));
                    long after =
                            d.count(
                                    "SELECT UNIX_TIMESTAMP(assigned_at)+16*60 FROM dispatch WHERE"
                                            + " active_order=2");
                    d.update("SET timestamp=?", after);
                    try {
                        assertEquals(
                                1, d.count("SELECT COUNT(*) FROM v_dispatch_overdue WHERE id=2"));
                    } finally {
                        d.update("SET timestamp=0");
                    }
                });
    }

    @Test
    void deviceHistoryCannotBeReassignedToAnotherElder() {
        rollback(
                () -> {
                    assertThrows(
                            RuntimeException.class,
                            () -> d.update("UPDATE device_binding SET elder_id=2 WHERE id=1"));
                });
    }

    @Test
    void closedBindingIntervalsCannotOverlap() {
        rollback(
                () -> {
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    d.insert(
                                            "INSERT INTO"
                                                + " device_binding(device_id,elder_id,bound_at,unbound_at)"
                                                + " VALUES(1,2,NOW()-INTERVAL 2 DAY,NOW()-INTERVAL"
                                                + " 1 DAY)"));
                });
    }

    @Test
    void disabledAccountRevokesExistingSession() {
        rollback(
                () -> {
                    String token =
                            (String)
                                    auth.login("family1", System.getenv("DEMO_PASSWORD"))
                                            .get("token");
                    d.update("UPDATE account SET active=0 WHERE id=2");
                    assertThrows(Unauthorized.class, () -> auth.require("Bearer " + token));
                });
    }

    @Test
    void malformedBatchIsClientError() throws Exception {
        String token = (String) auth.login("admin1", System.getenv("DEMO_PASSWORD")).get("token");
        mvc.perform(
                        post("/api/actions/vital.upload")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"samples\":[7]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerDoesNotReadAnotherInstitutionsOrders() throws Exception {
        String token = (String) auth.login("manager1", System.getenv("DEMO_PASSWORD")).get("token");
        var body =
                new ObjectMapper()
                        .readTree(
                                mvc.perform(
                                                get("/api/data/orders")
                                                        .header("Authorization", "Bearer " + token))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString());
        for (var row : body.get("rows")) {
            assertNotEquals(
                    2,
                    row.get("id").asInt(),
                    "Unassigned order is not an institution's order merely because it served this"
                            + " elder before");
        }
    }

    @Test
    void nullClaimOrdersAreRejected() {
        rollback(
                () -> {
                    assertThrows(
                            RuntimeException.class,
                            () -> d.call("submit_claim", 1, 1, "2099-01", "null"));
                });
    }

    @Test
    void recommendationAppliesQualificationAndRectification() {
        rollback(
                () -> {
                    var candidates =
                            (List<?>)
                                    service.execute(admin, "order.recommend", Map.of("orderId", 2));
                    assertEquals(1, candidates.size());
                    d.update("UPDATE inspection SET status='OPEN' WHERE id=1");
                    assertTrue(
                            ((List<?>)
                                            service.execute(
                                                    admin, "order.recommend", Map.of("orderId", 2)))
                                    .isEmpty());
                });
    }

    @Test
    void databaseRejectsAcceptWithExpiredCertificate() {
        rollback(
                () -> {
                    service.execute(
                            admin,
                            "order.assign",
                            Map.of("orderId", 2, "shiftId", 2, "reason", "数据库资格约束"));
                    d.update(
                            "UPDATE credential SET valid_until=NOW()-INTERVAL 1 SECOND WHERE id=1");
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    d.update(
                                            "UPDATE dispatch SET status='ACCEPTED' WHERE"
                                                    + " active_order=2"));
                });
    }

    @Test
    void rapidDeviceRebindPreservesBothIntervals() {
        rollback(
                () -> {
                    long first =
                            ((Number)
                                            service.execute(
                                                    admin,
                                                    "device.bind",
                                                    Map.of("deviceId", 1, "elderId", 1)))
                                    .longValue();
                    long second =
                            ((Number)
                                            service.execute(
                                                    admin,
                                                    "device.bind",
                                                    Map.of("deviceId", 1, "elderId", 1)))
                                    .longValue();
                    assertNotEquals(first, second);
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT COUNT(*) FROM device_binding WHERE device_id=1 AND"
                                            + " unbound_at IS NULL"));
                    assertEquals(1, d.count("SELECT elder_id FROM device_binding WHERE id=1"));
                });
    }

    @Test
    void familyCannotReportEmergencyForAnotherElder() {
        reject(
                () ->
                        service.execute(
                                family,
                                "event.create",
                                Map.of("elderId", 2, "level", 1, "description", "越权测试")));
    }

    @Test
    void changedRoleInvalidatesSession() {
        rollback(
                () -> {
                    String token =
                            (String)
                                    auth.login("family1", System.getenv("DEMO_PASSWORD"))
                                            .get("token");
                    d.update("UPDATE account SET role='ADMIN' WHERE id=2");
                    assertThrows(Unauthorized.class, () -> auth.require("Bearer " + token));
                });
    }

    @Test
    void allBusinessViewRoutesExecuteWithTheirIntendedRoles() throws Exception {
        Map<String, List<String>> routes =
                Map.of(
                        "admin1",
                        List.of(
                                "assessmentDue",
                                "dispatchPool",
                                "activePlans",
                                "waitingConfirmation",
                                "reassignments",
                                "currentDevices",
                                "abnormalVitals",
                                "pendingEvents",
                                "eligibleClaims",
                                "claimProgress",
                                "rectifications"),
                        "family1",
                        List.of("familyCare", "selfPay"),
                        "manager1",
                        List.of(
                                "credentialDue",
                                "shiftCapacity",
                                "hoursReview",
                                "monthlyHours",
                                "quality",
                                "organizationQuality"));
        for (var entry : routes.entrySet()) {
            String token =
                    (String)
                            auth.login(entry.getKey(), System.getenv("DEMO_PASSWORD")).get("token");
            for (String kind : entry.getValue()) {
                mvc.perform(get("/api/data/" + kind).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
            }
        }
    }

    @Test
    void familyCannotReadDispatchPool() throws Exception {
        String token = (String) auth.login("family1", System.getenv("DEMO_PASSWORD")).get("token");
        mvc.perform(get("/api/data/dispatchPool").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidDateIsClientError() throws Exception {
        String token = (String) auth.login("admin1", System.getenv("DEMO_PASSWORD")).get("token");
        mvc.perform(
                        post("/api/actions/order.create")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{\"elderId\":1,\"itemId\":1,\"startsAt\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
    }

    long completedEvidenceOrder(LocalDateTime start) {
        long sh =
                d.insert(
                        "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity,used_count)"
                                + " VALUES(1,?,?,1,1)",
                        start,
                        start.plusHours(1));
        long order =
                d.insert(
                        "INSERT INTO"
                            + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x)"
                            + " VALUES(1,1,1,?,?,'模拟地址','COMMUNITY',80,1,10000)",
                        start,
                        start.plusHours(1));
        long dispatch =
                d.insert(
                        "INSERT INTO dispatch(order_id,shift_id,status) VALUES(?,?,'FINISHED')",
                        order,
                        sh);
        d.insert(
                "INSERT INTO"
                    + " attendance(dispatch_id,check_in,check_out,location_note,fence_ok,status,effective_minutes)"
                    + " VALUES(?,?,?,'模拟证据',1,'VALID',60)",
                dispatch,
                start,
                start.plusHours(1));
        d.insert(
                "INSERT INTO"
                    + " service_record(dispatch_id,notes,confirmed_by,confirmed_at,confirmation)"
                    + " VALUES(?,'模拟服务',2,?,'家属确认')",
                dispatch,
                start.plusHours(1));
        d.update("UPDATE service_order SET status='COMPLETED' WHERE id=?", order);
        return order;
    }

    @Test
    void claimRollsBackAfterWritingFirstValidItem() {
        var start = LocalDateTime.now().minusDays(40).withNano(0);
        long order = new TransactionTemplate(tm).execute(tx -> completedEvidenceOrder(start));
        long headers = d.count("SELECT COUNT(*) FROM claim"),
                items = d.count("SELECT COUNT(*) FROM claim_item"),
                audit = d.count("SELECT COUNT(*) FROM audit_log");
        reject(
                () ->
                        d.call(
                                "submit_claim",
                                1,
                                1,
                                start.toString().substring(0, 7),
                                "[" + order + ",999999]"));
        assertEquals(headers, d.count("SELECT COUNT(*) FROM claim"));
        assertEquals(items, d.count("SELECT COUNT(*) FROM claim_item"));
        assertEquals(audit, d.count("SELECT COUNT(*) FROM audit_log"));
    }

    @Test
    void subsidyReviewAndPaymentPreserveEvidence() {
        rollback(
                () -> {
                    var start = LocalDateTime.now().minusDays(41).withNano(0);
                    long order = completedEvidenceOrder(start);
                    service.execute(
                            admin,
                            "claim.submit",
                            Map.of(
                                    "elderId",
                                    1,
                                    "policyId",
                                    1,
                                    "period",
                                    start.toString().substring(0, 7),
                                    "orderIds",
                                    List.of(order)));
                    long claim = d.count("SELECT claim_id FROM claim_item WHERE order_id=?", order);
                    var street = new Auth.User(5, "street1", "STREET", 1, 1L);
                    var regulator = new Auth.User(6, "regulator1", "REGULATOR", 1, 1L);
                    service.execute(
                            street,
                            "claim.review",
                            Map.of("claimId", claim, "approve", true, "reason", "核对订单与工时"));
                    service.execute(
                            regulator,
                            "claim.review",
                            Map.of("claimId", claim, "approve", true, "reason", "核对补贴政策"));
                    service.execute(
                            regulator,
                            "claim.pay",
                            Map.of("claimId", claim, "reference", "AUDIT-PAY-TEST"));
                    assertEquals(
                            "PAID",
                            d.one("SELECT status FROM claim WHERE id=?", claim).get("status"));
                    assertEquals(30, d.count("SELECT amount FROM payment WHERE claim_id=?", claim));
                    assertEquals(
                            60,
                            d.count(
                                    "SELECT effective_minutes FROM claim_item WHERE claim_id=?",
                                    claim));
                });
    }

    @Test
    @Order(2147483644)
    void concurrentOverlappingOrdersCannotShareWorker() throws Exception {
        List<Long> orders = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            orders.add(
                    d.insert(
                            "INSERT INTO"
                                + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x,position_y)"
                                + " SELECT"
                                + " elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x,position_y"
                                + " FROM service_order WHERE id=2"));
        }
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (long order : orders) {
                futures.add(
                        pool.submit(
                                () -> {
                                    barrier.await();
                                    try {
                                        service.execute(
                                                admin,
                                                "order.assign",
                                                Map.of(
                                                        "orderId", order, "shiftId", 2, "reason",
                                                        "并发时间冲突"));
                                        return true;
                                    } catch (RuntimeException ex) {
                                        return false;
                                    }
                                }));
            }
            int success = 0;
            for (var f : futures) {
                if (f.get(15, TimeUnit.SECONDS)) {
                    success++;
                }
            }
            assertEquals(1, success);
        } finally {
            pool.shutdownNow();
            for (long order : orders) {
                service.execute(admin, "order.cancel", Map.of("orderId", order, "reason", "测试结束"));
            }
        }
    }

    @Test
    void futureSampleRejected() {
        reject(
                () ->
                        d.insert(
                                "INSERT INTO"
                                    + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                                    + " VALUES(1,1,'future-audit',NOW()+INTERVAL 1"
                                    + " DAY,95,'TEST')"));
    }

    @Test
    void identityNormalizationPreventsDuplicate() {
        reject(
                () ->
                        service.execute(
                                admin,
                                "elder.create",
                                Map.of(
                                        "name",
                                        "重复档案",
                                        "identity",
                                        " synthetic-id-1 ",
                                        "birthDate",
                                        "1950-01-01",
                                        "address",
                                        "模拟地址",
                                        "x",
                                        10000,
                                        "y",
                                        0)));
    }

    @Test
    void orderInspectionBlocksItsResponsibleWorker() {
        rollback(
                () -> {
                    service.execute(
                            new Auth.User(6, "regulator1", "REGULATOR", 1, 1L),
                            "inspection.order",
                            Map.of(
                                    "orderId",
                                    1,
                                    "method",
                                    "PHONE",
                                    "score",
                                    50,
                                    "issue",
                                    "模拟抽查问题",
                                    "rectification",
                                    "复核服务",
                                    "requiresRectification",
                                    true));
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    service.execute(
                                            admin,
                                            "order.assign",
                                            Map.of("orderId", 2, "shiftId", 2, "reason", "整改限制")));
                });
    }

    @Test
    void unapprovedPaymentRejected() {
        rollback(
                () -> {
                    long claim =
                            d.insert(
                                    "INSERT INTO claim(elder_id,item_id,period)"
                                            + " VALUES(1,1,'2099-11')");
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    d.insert(
                                            "INSERT INTO payment(claim_id,reference_no,amount)"
                                                    + " VALUES(?,'INVALID-PAYMENT',1)",
                                            claim));
                });
    }

    @Test
    void familyConfirmationCannotBeForgedByManager() {
        rollback(
                () -> {
                    long order =
                            d.insert(
                                    "INSERT INTO"
                                        + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x,position_y)"
                                        + " SELECT"
                                        + " elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x,position_y"
                                        + " FROM service_order WHERE id=2");
                    long dispatch =
                            d.insert(
                                    "INSERT INTO dispatch(order_id,shift_id,status)"
                                            + " VALUES(?,2,'FINISHED')",
                                    order);
                    d.insert(
                            "INSERT INTO"
                                + " service_record(dispatch_id,notes,confirmed_by,confirmed_at,confirmation)"
                                + " VALUES(?,'测试',4,NOW(),'伪造家属身份')",
                            dispatch);
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    d.update(
                                            "UPDATE service_order SET status='COMPLETED' WHERE"
                                                    + " id=?",
                                            order));
                });
    }

    @Test
    @Order(2147483645)
    void failedReassignmentRestoresPriorDispatch() {
        service.execute(
                admin, "order.assign", Map.of("orderId", 2, "shiftId", 2, "reason", "回滚验证初次派单"));
        long before = d.count("SELECT id FROM v_dispatch_context WHERE active_order=2");
        assertThrows(
                RuntimeException.class,
                () ->
                        service.execute(
                                admin,
                                "order.assign",
                                Map.of("orderId", 2, "shiftId", 1, "reason", "故意选择不覆盖预约时间的旧班次")));
        assertEquals(before, d.count("SELECT id FROM v_dispatch_context WHERE active_order=2"));
        assertEquals(1, d.count("SELECT used_count FROM shift WHERE id=2"));
        service.execute(admin, "order.cancel", Map.of("orderId", 2, "reason", "测试结束释放名额"));
        d.update("UPDATE service_order SET status='PENDING' WHERE id=2");
    }

    @Test
    @Order(2147483646)
    void concurrentPlanCreationSerializes() throws Exception {
        var start = LocalDateTime.now().plusDays(120).withNano(0);
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> fs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            fs.add(
                    pool.submit(
                            () -> {
                                barrier.await();
                                try {
                                    new TransactionTemplate(tm)
                                            .execute(
                                                    s -> {
                                                        d.insert(
                                                                "INSERT INTO"
                                                                    + " care_plan(assessment_id,bundle_id,caregiver_id,starts_at,ends_at)"
                                                                    + " VALUES(1,1,1,?,?)",
                                                                start,
                                                                start.plusDays(1));
                                                        return null;
                                                    });
                                    return true;
                                } catch (RuntimeException e) {
                                    return false;
                                }
                            }));
        }
        int success = 0;
        for (var f : fs) {
            if (f.get(15, TimeUnit.SECONDS)) {
                success++;
            }
        }
        pool.shutdownNow();
        assertEquals(1, success);
        d.update(
                "UPDATE care_plan SET status='ENDED' WHERE assessment_id=1 AND starts_at=?", start);
    }

    @Test
    @Order(2147483647)
    void concurrentLastSlotOnlyOneSucceeds() throws Exception {
        d.update("UPDATE shift SET capacity=1 WHERE id=2");
        var o = d.one("SELECT * FROM service_order WHERE id=2");
        long extra =
                d.insert(
                        "INSERT INTO"
                            + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x,position_y)"
                            + " SELECT elder_id,item_id,community_id,starts_at+INTERVAL 2"
                            + " HOUR,ends_at+INTERVAL 2"
                            + " HOUR,address,channel,amount,created_by,position_x,position_y FROM"
                            + " service_order WHERE id=2");
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> fs = new ArrayList<>();
        for (long order : new long[] {2, extra}) {
            fs.add(
                    pool.submit(
                            () -> {
                                barrier.await();
                                try {
                                    service.execute(
                                            admin,
                                            "order.assign",
                                            Map.of(
                                                    "orderId", order, "shiftId", 2, "reason",
                                                    "并发最后名额"));
                                    return true;
                                } catch (RuntimeException e) {
                                    return false;
                                }
                            }));
        }
        int success = 0;
        for (var f : fs) {
            if (f.get(15, TimeUnit.SECONDS)) {
                success++;
            }
        }
        pool.shutdownNow();
        assertEquals(1, success);
        assertEquals(1, d.count("SELECT used_count FROM shift WHERE id=2"));
        for (long order : new long[] {2, extra}) {
            service.execute(admin, "order.cancel", Map.of("orderId", order, "reason", "并发测试结束"));
        }
        d.update("UPDATE service_order SET status='PENDING' WHERE id=2");
        d.update("UPDATE shift SET capacity=3 WHERE id=2");
    }
}
