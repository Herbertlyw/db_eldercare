package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.ReadService;
import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import edu.bjfu.eldercare.infrastructure.scheduling.Simulator;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"app.jobs=false"})
@AutoConfigureMockMvc
class WorkflowIntegrationTest {

    @Autowired Store d;

    @Autowired CareService service;

    @Autowired Auth auth;

    @Autowired Scope scope;

    @Autowired ReadService reads;

    @Autowired Simulator simulator;

    @Autowired MockMvc mvc;

    @Autowired PlatformTransactionManager tm;

    final Auth.User admin = u(1, "ADMIN"),
            family = u(1, "FAMILY"),
            worker = u(1, "CAREGIVER"),
            manager = u(1, "MANAGER"),
            street = u(1, "STREET"),
            reg = u(1, "REGULATOR");

    static Auth.User u(long community, String role) {
        int offset =
                List.of("ADMIN", "FAMILY", "CAREGIVER", "MANAGER", "STREET", "REGULATOR")
                                .indexOf(role)
                        + 1;
        return new Auth.User(
                (community - 1) * 6 + offset,
                role.toLowerCase() + community,
                role,
                community,
                community);
    }

    Map<String, Object> m(Object... a) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i < a.length; i += 2) {
            out.put(a[i].toString(), a[i + 1]);
        }
        return out;
    }

    Object run(Auth.User u, String action, Object... a) {
        return service.execute(u, action, m(a));
    }

    long add(Auth.User u, String action, Object... a) {
        return ((Number) run(u, action, a)).longValue();
    }

    void rollback(Runnable r) {
        new TransactionTemplate(tm)
                .execute(
                        tx -> {
                            tx.setRollbackOnly();
                            r.run();
                            return null;
                        });
    }

    long count(String table) {
        return d.count("SELECT COUNT(*) FROM " + table);
    }

    String state(String table, long id) {
        return d.one("SELECT status FROM " + table + " WHERE id=?", id).get("status").toString();
    }

    void denied(Runnable r) {
        assertThrows(Forbidden.class, r::run);
    }

    void invalid(Runnable r) {
        assertThrows(RuntimeException.class, r::run);
    }

    String login(String name) {
        return "Bearer " + auth.login(name, System.getenv("DEMO_PASSWORD")).get("token");
    }

    long complaint() {
        return add(
                family, "complaint.create", "orderId", 1, "rating", 2, "content", "模拟投诉：服务记录需要核实");
    }

    long manualInspection(boolean rectify) {
        return add(
                reg,
                "inspection.order",
                "orderId",
                1,
                "method",
                "PHONE",
                "score",
                rectify ? 50 : 95,
                "issue",
                rectify ? "记录不完整" : "无",
                "rectification",
                rectify ? "补充服务记录" : "无",
                "requiresRectification",
                rectify);
    }

    long event() {
        long vital =
                d.insert(
                        "INSERT INTO"
                            + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                            + " VALUES(1,1,?,NOW(6),100,'TEST')",
                        UUID.randomUUID().toString());
        return d.count("SELECT id FROM emergency WHERE vital_id=?", vital);
    }

    long recruitment() {
        return add(manager, "recruitment.create", "name", "模拟应聘人", "phone", "DEMO-NEW-PHONE");
    }

    long hire(long r) {
        return add(
                manager,
                "recruitment.review",
                "recruitmentId",
                r,
                "approve",
                true,
                "reason",
                "核验课程模拟材料",
                "username",
                "new-worker-" + r,
                "password",
                "Course-Only-Password!");
    }

    long completed(LocalDateTime start) {
        return completed(start, 1);
    }

    long completed(LocalDateTime start, long item) {
        long sh =
                d.insert(
                        "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity,used_count)"
                                + " VALUES(1,?,?,1,1)",
                        start,
                        start.plusHours(1));
        long o =
                d.insert(
                        "INSERT INTO"
                            + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,position_x,channel,amount,created_by)"
                            + " VALUES(1,?,1,?,?,'模拟地址',10000,'COMMUNITY',80,1)",
                        item,
                        start,
                        start.plusHours(1));
        long ds =
                d.insert(
                        "INSERT INTO dispatch(order_id,shift_id,status) VALUES(?,?,'FINISHED')",
                        o,
                        sh);
        d.insert(
                "INSERT INTO"
                    + " attendance(dispatch_id,check_in,check_out,location_note,fence_ok,checkout_fence_ok,status,effective_minutes)"
                    + " VALUES(?,?,?,'模拟历史证据',1,1,'VALID',60)",
                ds,
                start,
                start.plusHours(1));
        d.insert(
                "INSERT INTO"
                    + " service_record(dispatch_id,notes,confirmed_by,confirmed_at,confirmation)"
                    + " VALUES(?,'模拟服务记录',2,?,'模拟家属确认')",
                ds,
                start.plusHours(1));
        d.update("UPDATE service_order SET status='COMPLETED' WHERE id=?", o);
        return o;
    }

    long newClaim() {
        var start = LocalDateTime.now().minusDays(42).withNano(0);
        long order = completed(start);
        run(
                admin,
                "claim.submit",
                "elderId",
                1,
                "policyId",
                1,
                "period",
                start.toString().substring(0, 7),
                "orderIds",
                List.of(order));
        return d.count("SELECT claim_id FROM claim_item WHERE order_id=?", order);
    }

    @Test
    void tagAndContactMaintenanceIsScopedAndEncrypted() {
        rollback(
                () -> {
                    run(admin, "elder.tags", "elderId", 1, "tag", "LOW_INCOME", "enabled", true);
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT COUNT(*) FROM elder_tag WHERE elder_id=1 AND"
                                            + " tag='LOW_INCOME'"));
                    run(
                            admin,
                            "guardian.update",
                            "guardianId",
                            1,
                            "relation",
                            "女儿",
                            "phone",
                            "DEMO-UPDATED",
                            "active",
                            true);
                    var json =
                            new ObjectMapper()
                                    .findAndRegisterModules()
                                    .valueToTree(reads.details(family, "elders", 1));
                    assertTrue(json.toString().contains("DEMO-UPDATED"));
                    assertFalse(json.toString().contains("phone_cipher"));
                    assertNotEquals(
                            "DEMO-UPDATED",
                            d.one("SELECT phone_cipher FROM guardian WHERE id=1")
                                    .get("phone_cipher"));
                    denied(
                            () ->
                                    run(
                                            u(2, "ADMIN"),
                                            "elder.tags",
                                            "elderId",
                                            1,
                                            "tag",
                                            "ALONE",
                                            "enabled",
                                            false));
                });
    }

    @Test
    void inactiveGuardianLosesAccess() {
        rollback(
                () -> {
                    run(
                            admin,
                            "guardian.update",
                            "guardianId",
                            1,
                            "relation",
                            "子女",
                            "phone",
                            "DEMO",
                            "active",
                            false);
                    denied(() -> reads.details(family, "elders", 1));
                    denied(() -> scope.order(family, 1));
                });
    }

    @Test
    void planRevisionPreservesOldVersionAndFrequency() {
        rollback(
                () -> {
                    run(admin, "order.cancel", "orderId", 2);
                    long id =
                            add(
                                    admin,
                                    "plan.revise",
                                    "planId",
                                    1,
                                    "elderId",
                                    1,
                                    "assessmentId",
                                    1,
                                    "bundleId",
                                    1,
                                    "caregiverId",
                                    1,
                                    "startsAt",
                                    LocalDateTime.now().toString(),
                                    "endsAt",
                                    LocalDateTime.now().plusDays(20).toString(),
                                    "items",
                                    List.of(m("itemId", 1, "frequency", 3)),
                                    "reason",
                                    "重新安排每周服务频次");
                    assertEquals("ENDED", state("care_plan", 1));
                    assertEquals(
                            2, d.count("SELECT weekly_frequency FROM plan_item WHERE plan_id=1"));
                    assertEquals(
                            3,
                            d.count("SELECT weekly_frequency FROM plan_item WHERE plan_id=?", id));
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT COUNT(*) FROM audit_log WHERE row_id=? AND"
                                            + " operation='REVISE'",
                                    id));
                    run(admin, "plan.state", "planId", id, "status", "PAUSED", "reason", "家属申请暂缓");
                    assertEquals("PAUSED", state("care_plan", id));
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT COUNT(*) FROM audit_log WHERE row_id=? AND"
                                            + " operation='STATE_REASON'",
                                    id));
                });
    }

    @Test
    void pendingOrdersPreventChangingTheirPlan() {
        rollback(
                () ->
                        invalid(
                                () ->
                                        run(
                                                admin,
                                                "plan.state",
                                                "planId",
                                                1,
                                                "status",
                                                "ENDED",
                                                "reason",
                                                "仍有待派单")));
    }

    @Test
    void latestAssessmentOnlyProducesOneReminder() {
        rollback(
                () -> {
                    long id =
                            d.insert(
                                    "INSERT INTO"
                                        + " assessment(elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until)"
                                        + " VALUES(1,'课程复评',30,'SEVERE',1,NOW(),NOW()+INTERVAL 15"
                                        + " DAY)");
                    d.call("remind_assessments");
                    d.call("remind_assessments");
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT COUNT(*) FROM notification WHERE assessment_id=? AND"
                                            + " account_id=1",
                                    id));
                    long newer =
                            d.insert(
                                    "INSERT INTO"
                                        + " assessment(elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until)"
                                        + " VALUES(1,'课程复评',30,'SEVERE',1,NOW(),NOW()+INTERVAL 200"
                                        + " DAY)");
                    assertTrue(newer > id);
                    assertEquals(0, d.count("SELECT COUNT(*) FROM v_assessment_due WHERE id=1"));
                });
    }

    @Test
    void addressCoordinatesAreCheckedAndCopiedToOrder() {
        rollback(
                () -> {
                    invalid(
                            () ->
                                    run(
                                            admin,
                                            "elder.create",
                                            "name",
                                            "虚构老人",
                                            "identity",
                                            "NEW-INVALID",
                                            "birthDate",
                                            "1950-01-01",
                                            "address",
                                            "圈外",
                                            "x",
                                            20000,
                                            "y",
                                            0));
                    long o =
                            add(
                                    admin,
                                    "order.create",
                                    "elderId",
                                    1,
                                    "itemId",
                                    1,
                                    "startsAt",
                                    LocalDateTime.now().plusDays(3).toString(),
                                    "channel",
                                    "PHONE");
                    assertEquals(
                            10000, d.count("SELECT position_x FROM service_order WHERE id=?", o));
                    assertEquals(
                            "PHONE",
                            d.one("SELECT channel FROM service_order WHERE id=?", o)
                                    .get("channel"));
                });
    }

    @Test
    void signedLocationCannotBeReplacedByCallerFenceFlag() {
        rollback(
                () -> {
                    run(admin, "order.assign", "orderId", 2, "shiftId", 2, "reason", "围栏测试");
                    run(worker, "order.accept", "orderId", 2);
                    run(
                            worker,
                            "order.checkin",
                            "orderId",
                            2,
                            "location",
                            "圈外模拟",
                            "x",
                            15000,
                            "y",
                            0,
                            "fenceOk",
                            true);
                    assertEquals(
                            0,
                            d.count(
                                    "SELECT fence_ok FROM attendance a JOIN v_dispatch_context ds"
                                            + " ON ds.id=a.dispatch_id WHERE ds.order_id=2"));
                });
    }

    @Test
    void tooShortServiceRequiresReviewAndZeroMinutesCannotPass() {
        rollback(
                () -> {
                    run(admin, "order.assign", "orderId", 2, "shiftId", 2, "reason", "短时测试");
                    run(worker, "order.accept", "orderId", 2);
                    run(
                            worker,
                            "order.checkin",
                            "orderId",
                            2,
                            "location",
                            "模拟住址",
                            "x",
                            10000,
                            "y",
                            0);
                    run(
                            worker,
                            "order.finish",
                            "orderId",
                            2,
                            "notes",
                            "立即签退测试",
                            "x",
                            10000,
                            "y",
                            0);
                    long a =
                            d.count(
                                    "SELECT a.id FROM attendance a JOIN v_dispatch_context ds ON"
                                            + " ds.id=a.dispatch_id WHERE ds.order_id=2");
                    assertEquals("REVIEW", state("attendance", a));
                    invalid(
                            () ->
                                    run(
                                            manager,
                                            "hours.review",
                                            "attendanceId",
                                            a,
                                            "approve",
                                            true,
                                            "reason",
                                            "不能核准零分钟"));
                });
    }

    @Test
    void finalizedAssessmentAndAttendanceCannotBeRewritten() {
        rollback(
                () -> {
                    invalid(() -> d.update("UPDATE assessment SET score=99 WHERE id=1"));
                    invalid(
                            () ->
                                    d.update(
                                            "UPDATE attendance SET effective_minutes=999 WHERE"
                                                    + " id=1"));
                });
    }

    @Test
    void rejectedAssignmentReleasesCapacityAndKeepsReason() {
        rollback(
                () -> {
                    run(admin, "order.assign", "orderId", 2, "shiftId", 2, "reason", "安排服务");
                    run(worker, "order.reject", "orderId", 2, "reason", "临时身体不适");
                    assertEquals("PENDING", state("service_order", 2));
                    assertEquals(0, d.count("SELECT used_count FROM shift WHERE id=2"));
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT COUNT(*) FROM dispatch WHERE order_id=2 AND"
                                            + " status='REJECTED' AND reason='临时身体不适'"));
                });
    }

    @Test
    void recruitmentHireCreatesAccountAndDefaultAreaOnce() {
        rollback(
                () -> {
                    long r = recruitment(), c = hire(r);
                    assertEquals("HIRED", state("recruitment", r));
                    assertEquals(
                            1,
                            d.count(
                                    "SELECT COUNT(*) FROM caregiver_area WHERE caregiver_id=? AND"
                                            + " community_id=1",
                                    c));
                    invalid(() -> hire(r));
                    denied(
                            () ->
                                    run(
                                            u(2, "MANAGER"),
                                            "recruitment.review",
                                            "recruitmentId",
                                            r,
                                            "approve",
                                            false,
                                            "reason",
                                            "越权"));
                });
    }

    @Test
    void recruitmentRejectionCreatesNoAccount() {
        rollback(
                () -> {
                    long before = count("account"), r = recruitment();
                    run(
                            manager,
                            "recruitment.review",
                            "recruitmentId",
                            r,
                            "approve",
                            false,
                            "reason",
                            "模拟资料不全");
                    assertEquals("REJECTED", state("recruitment", r));
                    assertEquals(before, count("account"));
                });
    }

    @Test
    void trainingEnrollResultAndQualificationAreIndependent() {
        rollback(
                () -> {
                    long c = hire(recruitment());
                    long tr =
                            add(
                                    manager,
                                    "training.create",
                                    "title",
                                    "模拟岗前培训",
                                    "startsAt",
                                    LocalDateTime.now().minusDays(2).toString(),
                                    "endsAt",
                                    LocalDateTime.now().minusDays(1).toString());
                    long record =
                            add(manager, "training.enroll", "trainingId", tr, "caregiverId", c);
                    run(manager, "training.result", "recordId", record, "score", 60);
                    assertEquals(
                            "PASS",
                            d.one("SELECT result FROM training_record WHERE id=?", record)
                                    .get("result"));
                    invalid(() -> run(manager, "training.result", "recordId", record, "score", 90));
                    assertEquals(
                            0, d.count("SELECT COUNT(*) FROM credential WHERE caregiver_id=?", c));
                });
    }

    @Test
    void futureTrainingAndForeignEnrollmentAreRejected() {
        rollback(
                () -> {
                    long tr =
                            add(
                                    manager,
                                    "training.create",
                                    "title",
                                    "未开始培训",
                                    "startsAt",
                                    LocalDateTime.now().plusDays(2).toString(),
                                    "endsAt",
                                    LocalDateTime.now().plusDays(3).toString());
                    long r = add(manager, "training.enroll", "trainingId", tr, "caregiverId", 1);
                    invalid(() -> run(manager, "training.result", "recordId", r, "score", 99));
                    denied(
                            () ->
                                    run(
                                            manager,
                                            "training.enroll",
                                            "trainingId",
                                            tr,
                                            "caregiverId",
                                            2));
                    invalid(
                            () ->
                                    run(
                                            manager,
                                            "training.enroll",
                                            "trainingId",
                                            tr,
                                            "caregiverId",
                                            1));
                });
    }

    @Test
    void untrainedWorkerCannotBeAssignedDespiteCertificate() {
        rollback(
                () -> {
                    long c = hire(recruitment());
                    run(
                            manager,
                            "credential.create",
                            "caregiverId",
                            c,
                            "itemId",
                            1,
                            "certificateNo",
                            "COURSE-CERT",
                            "level",
                            "课程资格",
                            "validFrom",
                            LocalDateTime.now().minusDays(1).toString(),
                            "validUntil",
                            LocalDateTime.now().plusDays(30).toString());
                    var sh = d.one("SELECT starts_at,ends_at FROM shift WHERE id=2");
                    long s =
                            d.insert(
                                    "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity)"
                                            + " VALUES(?,?,?,2)",
                                    c,
                                    sh.get("starts_at"),
                                    sh.get("ends_at"));
                    invalid(
                            () ->
                                    run(
                                            admin,
                                            "order.assign",
                                            "orderId",
                                            2,
                                            "shiftId",
                                            s,
                                            "reason",
                                            "未培训"));
                });
    }

    @Test
    void complaintCompletesWithFourAppendOnlySteps() {
        rollback(
                () -> {
                    long c = complaint();
                    run(
                            admin,
                            "complaint.assign",
                            "complaintId",
                            c,
                            "organizationId",
                            1,
                            "reason",
                            "受理核查");
                    run(manager, "complaint.reply", "complaintId", c, "result", "已向家属解释并补记");
                    run(family, "complaint.close", "complaintId", c, "reason", "已核实处理情况");
                    assertEquals("CLOSED", state("complaint", c));
                    assertEquals(
                            4,
                            d.count("SELECT COUNT(*) FROM complaint_log WHERE complaint_id=?", c));
                    invalid(() -> d.update("DELETE FROM complaint_log WHERE complaint_id=?", c));
                });
    }

    @Test
    void complaintCannotSkipStepsOrBeHandledByForeignInstitution() {
        rollback(
                () -> {
                    long c = complaint();
                    invalid(
                            () ->
                                    run(
                                            family,
                                            "complaint.close",
                                            "complaintId",
                                            c,
                                            "reason",
                                            "未回复"));
                    invalid(
                            () ->
                                    run(
                                            admin,
                                            "complaint.assign",
                                            "complaintId",
                                            c,
                                            "organizationId",
                                            2,
                                            "reason",
                                            "非承接机构"));
                    run(
                            admin,
                            "complaint.assign",
                            "complaintId",
                            c,
                            "organizationId",
                            1,
                            "reason",
                            "受理");
                    denied(
                            () ->
                                    run(
                                            u(2, "MANAGER"),
                                            "complaint.reply",
                                            "complaintId",
                                            c,
                                            "result",
                                            "越权"));
                });
    }

    @Test
    void ordinaryReviewIsClosedAndRequiresCompletedOrder() {
        rollback(
                () -> {
                    long c =
                            add(
                                    family,
                                    "review.create",
                                    "orderId",
                                    1,
                                    "rating",
                                    5,
                                    "content",
                                    "模拟满意评价");
                    assertEquals("CLOSED", state("complaint", c));
                    assertEquals(
                            "REVIEW",
                            d.one("SELECT kind FROM complaint WHERE id=?", c).get("kind"));
                    invalid(
                            () ->
                                    run(
                                            family,
                                            "review.create",
                                            "orderId",
                                            2,
                                            "rating",
                                            5,
                                            "content",
                                            "尚未完成"));
                });
    }

    @Test
    void deviceUnbindRetainsHistoricalSamplesAndBlocksRetirementUntilUnbound() {
        rollback(
                () -> {
                    invalid(() -> run(admin, "device.status", "deviceId", 1, "status", "RETIRED"));
                    long old = d.count("SELECT COUNT(*) FROM vital WHERE binding_id=1");
                    run(admin, "device.unbind", "deviceId", 1);
                    run(admin, "device.status", "deviceId", 1, "status", "RETIRED");
                    assertEquals(old, d.count("SELECT COUNT(*) FROM vital WHERE binding_id=1"));
                    assertEquals(
                            0,
                            d.count(
                                    "SELECT COUNT(*) FROM device_binding WHERE device_id=1 AND"
                                            + " unbound_at IS NULL"));
                });
    }

    @Test
    void scheduledSimulatorCanStartStopAndAvoidDuplicateTicks() {
        rollback(
                () -> {
                    long before = count("vital");
                    run(
                            admin,
                            "device.simulation",
                            "deviceId",
                            1,
                            "enabled",
                            true,
                            "metricId",
                            1,
                            "seconds",
                            60);
                    assertEquals(1, simulator.tick());
                    assertEquals(0, simulator.tick());
                    assertEquals(before + 1, count("vital"));
                    run(
                            admin,
                            "device.simulation",
                            "deviceId",
                            1,
                            "enabled",
                            false,
                            "metricId",
                            1,
                            "seconds",
                            60);
                    assertEquals(0, simulator.tick());
                });
    }

    @Test
    void simulatorRequiresBoundActiveDeviceAndValidMetric() {
        rollback(
                () -> {
                    run(admin, "device.unbind", "deviceId", 1);
                    invalid(
                            () ->
                                    run(
                                            admin,
                                            "device.simulation",
                                            "deviceId",
                                            1,
                                            "enabled",
                                            true,
                                            "metricId",
                                            1,
                                            "seconds",
                                            60));
                    invalid(() -> run(admin, "simulator.run", "metricId", 999999));
                    denied(
                            () ->
                                    run(
                                            u(2, "ADMIN"),
                                            "device.simulation",
                                            "deviceId",
                                            1,
                                            "enabled",
                                            true,
                                            "metricId",
                                            1,
                                            "seconds",
                                            60));
                });
    }

    @Test
    void responsibilityWorkerReadsAndCompletesEventWithSeparateArrival() {
        rollback(
                () -> {
                    long e = event();
                    assertNotNull(reads.details(worker, "events", e));
                    run(worker, "event.confirm", "eventId", e, "reason", "已电话核实");
                    run(worker, "event.respond", "eventId", e);
                    invalid(() -> run(worker, "event.close", "eventId", e, "result", "还未到场"));
                    run(worker, "event.arrive", "eventId", e, "note", "已到现场");
                    Object arrival =
                            d.one("SELECT arrived_at FROM emergency WHERE id=?", e)
                                    .get("arrived_at");
                    run(worker, "event.close", "eventId", e, "result", "模拟处置完成");
                    assertEquals(
                            arrival,
                            d.one("SELECT arrived_at FROM emergency WHERE id=?", e)
                                    .get("arrived_at"));
                    assertEquals("CLOSED", state("emergency", e));
                    assertEquals(4, d.count("SELECT COUNT(*) FROM event_step WHERE event_id=?", e));
                });
    }

    @Test
    void falseAlarmRequiresReasonAndCannotBeReopened() {
        rollback(
                () -> {
                    long e = event();
                    run(admin, "event.dismiss", "eventId", e, "reason", "核实为设备误报");
                    assertEquals("FALSE_ALARM", state("emergency", e));
                    invalid(() -> run(admin, "event.respond", "eventId", e));
                    invalid(() -> d.update("UPDATE emergency SET status='OPEN' WHERE id=?", e));
                });
    }

    @Test
    void claimRejectionResubmissionKeepsItemsAndPriorReview() {
        rollback(
                () -> {
                    long c = newClaim();
                    long items = d.count("SELECT COUNT(*) FROM claim_item WHERE claim_id=?", c);
                    run(street, "claim.review", "claimId", c, "approve", false, "reason", "补充证明");
                    assertEquals("REJECTED", state("claim", c));
                    run(admin, "claim.resubmit", "claimId", c, "reason", "已补充家属确认说明");
                    assertEquals("SUBMITTED", state("claim", c));
                    assertEquals(
                            items, d.count("SELECT COUNT(*) FROM claim_item WHERE claim_id=?", c));
                    assertEquals(
                            1, d.count("SELECT COUNT(*) FROM claim_review WHERE claim_id=?", c));
                    assertEquals(
                            1, d.count("SELECT COUNT(*) FROM claim_evidence WHERE claim_id=?", c));
                    run(street, "claim.review", "claimId", c, "approve", true, "reason", "补充材料通过");
                    assertEquals(
                            2, d.count("SELECT COUNT(*) FROM claim_review WHERE claim_id=?", c));
                });
    }

    @Test
    void familyCanReadClaimEvidenceButCannotChangeIt() {
        rollback(
                () -> {
                    long c = newClaim();
                    run(admin, "claim.evidence", "claimId", c, "name", "补充说明", "content", "测试归档材料");
                    var out =
                            new ObjectMapper()
                                    .findAndRegisterModules()
                                    .valueToTree(reads.details(family, "claims", c));
                    assertTrue(out.toString().contains("测试归档材料"));
                    assertTrue(out.has("订单与核算依据"));
                    denied(
                            () ->
                                    run(
                                            family,
                                            "claim.evidence",
                                            "claimId",
                                            c,
                                            "name",
                                            "越权",
                                            "content",
                                            "越权"));
                    invalid(
                            () ->
                                    d.update(
                                            "UPDATE claim_evidence SET content='改写' WHERE"
                                                    + " claim_id=?",
                                            c));
                });
    }

    @Test
    void policyAreaCannotExceedRegulatorsScope() {
        rollback(
                () -> {
                    denied(
                            () ->
                                    run(
                                            reg,
                                            "policy.create",
                                            "name",
                                            "虚构规则",
                                            "itemId",
                                            1,
                                            "level",
                                            "SEVERE",
                                            "rate",
                                            30,
                                            "startsAt",
                                            LocalDateTime.now().minusDays(100).toString(),
                                            "endsAt",
                                            LocalDateTime.now().plusDays(100).toString(),
                                            "cycle",
                                            "MONTH",
                                            "minimumAge",
                                            60,
                                            "tag",
                                            "NONE",
                                            "communityIds",
                                            List.of(5)));
                });
    }

    @Test
    void ratioSamplingStoresPopulationAndAllSelectedIds() {
        rollback(
                () -> {
                    long b =
                            add(
                                    reg,
                                    "inspection.batch",
                                    "communityId",
                                    1,
                                    "target",
                                    "ORDER",
                                    "startsAt",
                                    LocalDateTime.now().minusDays(30).toString(),
                                    "endsAt",
                                    LocalDateTime.now().toString(),
                                    "ratio",
                                    0.25);
                    assertEquals(
                            1, d.count("SELECT sample_count FROM inspection_batch WHERE id=?", b));
                    assertEquals(
                            d.count("SELECT sample_count FROM inspection_batch WHERE id=?", b),
                            d.count("SELECT COUNT(*) FROM inspection WHERE batch_id=?", b));
                    assertEquals(
                            "PENDING",
                            d.one("SELECT status FROM inspection WHERE batch_id=?", b)
                                    .get("status"));
                    invalid(() -> d.update("UPDATE inspection_batch SET ratio=1 WHERE id=?", b));
                });
    }

    @Test
    void caregiverSamplingAndRatioBoundaries() {
        rollback(
                () -> {
                    long b =
                            add(
                                    reg,
                                    "inspection.batch",
                                    "communityId",
                                    1,
                                    "target",
                                    "CAREGIVER",
                                    "startsAt",
                                    LocalDateTime.now().minusDays(30).toString(),
                                    "endsAt",
                                    LocalDateTime.now().toString(),
                                    "ratio",
                                    1);
                    assertEquals(
                            1, d.count("SELECT caregiver_id FROM inspection WHERE batch_id=?", b));
                    invalid(
                            () ->
                                    run(
                                            reg,
                                            "inspection.batch",
                                            "communityId",
                                            1,
                                            "target",
                                            "ORDER",
                                            "startsAt",
                                            LocalDateTime.now().minusDays(30).toString(),
                                            "endsAt",
                                            LocalDateTime.now().toString(),
                                            "ratio",
                                            0));
                    denied(
                            () ->
                                    run(
                                            reg,
                                            "inspection.batch",
                                            "communityId",
                                            5,
                                            "target",
                                            "ORDER",
                                            "startsAt",
                                            LocalDateTime.now().minusDays(30).toString(),
                                            "endsAt",
                                            LocalDateTime.now().toString(),
                                            "ratio",
                                            0.2));
                });
    }

    @Test
    void rectificationReturnResubmitAndApproveKeepsBothAttempts() {
        rollback(
                () -> {
                    long i = manualInspection(true);
                    invalid(
                            () ->
                                    run(
                                            reg,
                                            "inspection.close",
                                            "inspectionId",
                                            i,
                                            "approve",
                                            true,
                                            "result",
                                            "缺少机构材料"));
                    run(
                            manager,
                            "rectification.submit",
                            "inspectionId",
                            i,
                            "measures",
                            "补全服务记录",
                            "evidence",
                            "第一次说明");
                    assertEquals("REVIEW", state("inspection", i));
                    run(
                            reg,
                            "inspection.close",
                            "inspectionId",
                            i,
                            "approve",
                            false,
                            "result",
                            "证据不充分");
                    assertEquals("OPEN", state("inspection", i));
                    run(
                            manager,
                            "rectification.submit",
                            "inspectionId",
                            i,
                            "measures",
                            "补充家属回访",
                            "evidence",
                            "第二次说明");
                    run(
                            reg,
                            "inspection.close",
                            "inspectionId",
                            i,
                            "approve",
                            true,
                            "result",
                            "复核通过");
                    assertEquals("CLOSED", state("inspection", i));
                    assertEquals(
                            2,
                            d.count(
                                    "SELECT COUNT(*) FROM rectification_submission WHERE"
                                            + " inspection_id=?",
                                    i));
                });
    }

    @Test
    void passedInspectionDoesNotBlockWorkerAndPublicNoticeHidesPrivateDetails() {
        rollback(
                () -> {
                    long i = manualInspection(false);
                    assertEquals("CLOSED", state("inspection", i));
                    long notice = add(reg, "notice.publish", "inspectionId", i);
                    String body =
                            d.one("SELECT body FROM public_notice WHERE id=?", notice)
                                    .get("body")
                                    .toString();
                    assertFalse(body.contains("演示老人"));
                    assertFalse(body.contains("演示地址"));
                    assertFalse(body.contains("记录不完整"));
                    invalid(() -> run(reg, "notice.publish", "inspectionId", i));
                    run(reg, "notice.withdraw", "noticeId", notice, "reason", "演示撤回");
                    assertNotNull(
                            d.one("SELECT withdrawn_at FROM public_notice WHERE id=?", notice)
                                    .get("withdrawn_at"));
                });
    }

    @Test
    void institutionReportCanBeReturnedAndResubmittedWithHistory() {
        rollback(
                () -> {
                    long r =
                            add(
                                    manager,
                                    "organization.submit",
                                    "period",
                                    "2026-11",
                                    "summary",
                                    "课程模拟月报",
                                    "evidence",
                                    "相关记录编号");
                    run(
                            reg,
                            "organization.review",
                            "reportId",
                            r,
                            "approve",
                            false,
                            "reason",
                            "补充整改证明");
                    long next =
                            add(
                                    manager,
                                    "organization.submit",
                                    "period",
                                    "2026-11",
                                    "summary",
                                    "补充月报",
                                    "evidence",
                                    "已补充");
                    assertNotEquals(r, next);
                    assertEquals("RETURNED", state("organization_report", r));
                    run(
                            reg,
                            "organization.review",
                            "reportId",
                            next,
                            "approve",
                            true,
                            "reason",
                            "材料齐备");
                    assertEquals("ACCEPTED", state("organization_report", next));
                    denied(
                            () ->
                                    run(
                                            u(5, "REGULATOR"),
                                            "organization.review",
                                            "reportId",
                                            r,
                                            "approve",
                                            true,
                                            "reason",
                                            "越权"));
                });
    }

    @Test
    void streetAndDistrictScopesContainTwoAndFourCommunities() {
        rollback(
                () -> {
                    var a =
                            new ObjectMapper()
                                    .findAndRegisterModules()
                                    .valueToTree(reads.list(street, "jurisdictionStats", 0, 100));
                    var b =
                            new ObjectMapper()
                                    .findAndRegisterModules()
                                    .valueToTree(reads.list(reg, "jurisdictionStats", 0, 100));
                    assertEquals(2, a.get("total").asInt());
                    assertEquals(4, b.get("total").asInt());
                    scope.community(reg, 4);
                    denied(() -> scope.community(reg, 5));
                    assertTrue(b.get("rows").get(0).has("coverage_rate"));
                });
    }

    @Test
    void supplementalReadRoutesAndDetailsReturnRealData() throws Exception {
        var roles =
                Map.of(
                        "admin1",
                        List.of("complaints", "communities", "accounts", "metrics"),
                        "manager1",
                        List.of(
                                "recruitments",
                                "trainings",
                                "trainingRecords",
                                "organizationReports",
                                "areas",
                                "managerDispatchPool"),
                        "regulator1",
                        List.of("inspectionBatches", "jurisdictionStats", "publicNotices"));
        for (var entry : roles.entrySet()) {
            String token = login(entry.getKey());
            for (String kind : entry.getValue()) {
                mvc.perform(get("/api/data/" + kind).header("Authorization", token))
                        .andExpect(status().isOk());
            }
        }
        String token = login("family1");
        for (String kind :
                List.of("elders", "plans", "orders", "claims", "devices", "events", "complaints")) {
            mvc.perform(get("/api/details/" + kind + "/1").header("Authorization", token))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/details/claims/2").header("Authorization", token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/public/notices")).andExpect(status().isOk());
    }

    long newItem() {
        long item =
                add(
                        admin,
                        "catalog.create",
                        "name",
                        "课程测试服务",
                        "category",
                        "助餐",
                        "unit",
                        "VISIT",
                        "price",
                        80,
                        "duration",
                        60,
                        "subsidizable",
                        true);
        d.insert("INSERT INTO plan_item(plan_id,item_id,weekly_frequency) VALUES(1,?,1)", item);
        return item;
    }

    long policy(long item, String cycle, long age, String tag, List<? extends Number> areas) {
        return add(
                reg,
                "policy.create",
                "name",
                "课程测试政策",
                "itemId",
                item,
                "level",
                "SEVERE",
                "rate",
                100,
                "startsAt",
                LocalDateTime.now().minusDays(365).toString(),
                "endsAt",
                LocalDateTime.now().plusDays(365).toString(),
                "cycle",
                cycle,
                "minimumAge",
                age,
                "tag",
                tag,
                "communityIds",
                areas);
    }

    @Test
    void monthQuarterAndYearPoliciesUseTheirOwnPeriodAndCapAtPrice() {
        rollback(
                () -> {
                    var start = LocalDateTime.now().minusDays(20).withNano(0);
                    for (String cycle : List.of("MONTH", "QUARTER", "YEAR")) {
                        long item = newItem(),
                                p = policy(item, cycle, 60, "ALONE", List.of(1, 2)),
                                o = completed(start, item);
                        String period =
                                cycle.equals("MONTH")
                                        ? start.toString().substring(0, 7)
                                        : cycle.equals("YEAR")
                                                ? "" + start.getYear()
                                                : start.getYear()
                                                        + "-Q"
                                                        + ((start.getMonthValue() - 1) / 3 + 1);
                        run(
                                admin,
                                "claim.submit",
                                "elderId",
                                1,
                                "policyId",
                                p,
                                "period",
                                period,
                                "orderIds",
                                List.of(o));
                        long c = d.count("SELECT claim_id FROM claim_item WHERE order_id=?", o);
                        assertEquals(80, d.count("SELECT amount FROM claim WHERE id=?", c));
                        assertEquals(
                                60,
                                d.count(
                                        "SELECT effective_minutes FROM claim_item WHERE order_id=?",
                                        o));
                        assertEquals(
                                100,
                                d.count(
                                        "SELECT rate_snapshot FROM claim_item WHERE order_id=?",
                                        o));
                        start = start.minusDays(1);
                    }
                });
    }

    @Test
    void annualAndMonthlyApplicationsCannotOverlapForSameItem() {
        rollback(
                () -> {
                    long item = newItem(),
                            monthly = policy(item, "MONTH", 0, "NONE", List.of(1)),
                            annual = policy(item, "YEAR", 0, "NONE", List.of(1));
                    var start = LocalDateTime.now().minusDays(25).withNano(0);
                    long o = completed(start, item);
                    run(
                            admin,
                            "claim.submit",
                            "elderId",
                            1,
                            "policyId",
                            monthly,
                            "period",
                            start.toString().substring(0, 7),
                            "orderIds",
                            List.of(o));
                    long before = count("claim");
                    invalid(
                            () ->
                                    run(
                                            admin,
                                            "claim.submit",
                                            "elderId",
                                            1,
                                            "policyId",
                                            annual,
                                            "period",
                                            "" + start.getYear(),
                                            "orderIds",
                                            List.of(o)));
                    assertEquals(before, count("claim"));
                });
    }

    @Test
    void policyConditionsAndPeriodFormatAreActuallyEnforced() {
        rollback(
                () -> {
                    long item = newItem();
                    var start = LocalDateTime.now().minusDays(25).withNano(0);
                    long o = completed(start, item);
                    String period = start.toString().substring(0, 7);
                    for (long p :
                            List.of(
                                    policy(item, "MONTH", 120, "NONE", List.of(1)),
                                    policy(item, "MONTH", 0, "LOW_INCOME", List.of(1)),
                                    policy(item, "MONTH", 0, "NONE", List.of(2)))) {
                        invalid(
                                () ->
                                        run(
                                                admin,
                                                "claim.submit",
                                                "elderId",
                                                1,
                                                "policyId",
                                                p,
                                                "period",
                                                period,
                                                "orderIds",
                                                List.of(o)));
                    }
                    long p = policy(item, "QUARTER", 0, "NONE", List.of(1));
                    invalid(
                            () ->
                                    run(
                                            admin,
                                            "claim.submit",
                                            "elderId",
                                            1,
                                            "policyId",
                                            p,
                                            "period",
                                            period,
                                            "orderIds",
                                            List.of(o)));
                });
    }

    @Test
    void failedRecruitmentHireRollsBackCreatedAccountAndWorker() {
        rollback(
                () -> {
                    long r = recruitment(), a = count("account"), c = count("caregiver");
                    invalid(
                            () ->
                                    run(
                                            manager,
                                            "recruitment.review",
                                            "recruitmentId",
                                            r,
                                            "approve",
                                            true,
                                            "reason",
                                            "错误输入",
                                            "username",
                                            "family1",
                                            "password",
                                            "Course-Only-Password!"));
                    assertEquals(a, count("account"));
                    assertEquals(c, count("caregiver"));
                    assertEquals("APPLIED", state("recruitment", r));
                });
    }

    @Test
    void sampleResultCanPassDirectlyOrEnterRectification() {
        rollback(
                () -> {
                    long batch =
                            add(
                                    reg,
                                    "inspection.batch",
                                    "communityId",
                                    1,
                                    "target",
                                    "ORDER",
                                    "startsAt",
                                    LocalDateTime.now().minusDays(30).toString(),
                                    "endsAt",
                                    LocalDateTime.now().toString(),
                                    "ratio",
                                    1);
                    long inspection = d.count("SELECT id FROM inspection WHERE batch_id=?", batch);
                    run(
                            reg,
                            "inspection.record",
                            "inspectionId",
                            inspection,
                            "method",
                            "VISIT",
                            "score",
                            59,
                            "issue",
                            "发现问题",
                            "requiresRectification",
                            false,
                            "rectification",
                            "补充记录");
                    assertEquals("OPEN", state("inspection", inspection));
                    invalid(
                            () ->
                                    run(
                                            reg,
                                            "inspection.record",
                                            "inspectionId",
                                            inspection,
                                            "method",
                                            "PHONE",
                                            "score",
                                            95,
                                            "issue",
                                            "重复登记",
                                            "requiresRectification",
                                            false,
                                            "rectification",
                                            "无"));
                    denied(
                            () ->
                                    run(
                                            u(2, "MANAGER"),
                                            "rectification.submit",
                                            "inspectionId",
                                            inspection,
                                            "measures",
                                            "越权",
                                            "evidence",
                                            "越权"));
                });
    }

    @Test
    void trainingScoreBelowThresholdCannotBeSilentlyOverwritten() {
        rollback(
                () -> {
                    long tr =
                            add(
                                    manager,
                                    "training.create",
                                    "title",
                                    "模拟补训",
                                    "startsAt",
                                    LocalDateTime.now().minusDays(2).toString(),
                                    "endsAt",
                                    LocalDateTime.now().minusDays(1).toString());
                    long r = add(manager, "training.enroll", "trainingId", tr, "caregiverId", 1);
                    run(manager, "training.result", "recordId", r, "score", 59);
                    assertEquals(
                            "FAIL",
                            d.one("SELECT result FROM training_record WHERE id=?", r)
                                    .get("result"));
                    invalid(
                            () ->
                                    d.update(
                                            "UPDATE training_record SET score=60,result='PASS'"
                                                    + " WHERE id=?",
                                            r));
                });
    }

    @Test
    void publicNoticeEndpointNeverIncludesSourcePersonalColumns() throws Exception {
        String text =
                mvc.perform(get("/api/public/notices"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        for (String key :
                List.of(
                        "identity",
                        "address",
                        "elder_id",
                        "phone_cipher",
                        "close_reason",
                        "complaint")) {
            assertFalse(text.contains(key));
        }
    }

    @Test
    void inactiveFamilyRelationIsExcludedFromCareSummary() {
        rollback(
                () -> {
                    run(
                            admin,
                            "guardian.update",
                            "guardianId",
                            1,
                            "relation",
                            "子女",
                            "phone",
                            "DEMO",
                            "active",
                            false);
                    assertEquals(
                            0, d.count("SELECT COUNT(*) FROM v_family_care WHERE account_id=2"));
                });
    }

    @Test
    void manualSimulationImmediatelyAfterRebindUsesMicrosecondTime() {
        rollback(
                () -> {
                    run(admin, "device.bind", "deviceId", 1, "elderId", 1);
                    long before = count("vital");
                    assertEquals(
                            1, ((Number) run(admin, "simulator.run", "metricId", 1)).intValue());
                    assertEquals(before + 1, count("vital"));
                });
    }

    @Test
    void complaintBeforeDispatchCanBeHandledByCommunity() {
        rollback(
                () -> {
                    long c =
                            add(
                                    family,
                                    "complaint.create",
                                    "orderId",
                                    2,
                                    "rating",
                                    2,
                                    "content",
                                    "待派单咨询未得到处理");
                    run(admin, "complaint.assign", "complaintId", c, "reason", "社区自行核查调度情况");
                    denied(
                            () ->
                                    run(
                                            manager,
                                            "complaint.reply",
                                            "complaintId",
                                            c,
                                            "result",
                                            "尚未承接"));
                    run(admin, "complaint.reply", "complaintId", c, "result", "已告知调度安排");
                    run(family, "complaint.close", "complaintId", c, "reason", "收到社区说明");
                    assertEquals("CLOSED", state("complaint", c));
                    assertEquals(
                            4,
                            d.count("SELECT COUNT(*) FROM complaint_log WHERE complaint_id=?", c));
                });
    }
}
