package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.*;

import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.ReadService;
import edu.bjfu.eldercare.application.port.Crypto;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.infrastructure.bootstrap.DemoScenarios;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"app.jobs=false"})
class DemoScenariosIntegrationTest {
    @Autowired Store db;
    @Autowired Crypto crypto;
    @Autowired PlatformTransactionManager tm;
    @Autowired CareService service;
    @Autowired ReadService reads;

    private void rollback(Runnable checks) {
        new TransactionTemplate(tm)
                .execute(
                        status -> {
                            status.setRollbackOnly();
                            checks.run();
                            return null;
                        });
    }

    private void populate() {
        new DemoScenarios(db, crypto).run();
    }

    @Test
    void allBaseTableEnumValuesHaveExamples() {
        rollback(
                () -> {
                    populate();
                    var columns =
                            db.rows(
                                    "SELECT c.TABLE_NAME,c.COLUMN_NAME,c.COLUMN_TYPE FROM"
                                        + " information_schema.COLUMNS c JOIN"
                                        + " information_schema.TABLES t ON"
                                        + " t.TABLE_SCHEMA=c.TABLE_SCHEMA AND"
                                        + " t.TABLE_NAME=c.TABLE_NAME WHERE"
                                        + " c.TABLE_SCHEMA=DATABASE() AND c.DATA_TYPE='enum' AND"
                                        + " t.TABLE_TYPE='BASE TABLE'");
                    assertTrue(columns.size() >= 30);
                    for (var column : columns) {
                        String table = column.get("TABLE_NAME").toString(),
                                field = column.get("COLUMN_NAME").toString();
                        var values =
                                Pattern.compile("'([^']+)'")
                                        .matcher(column.get("COLUMN_TYPE").toString());
                        while (values.find()) {
                            String value = values.group(1);
                            assertTrue(
                                    db.count(
                                                    "SELECT COUNT(*) FROM `"
                                                            + table
                                                            + "` WHERE `"
                                                            + field
                                                            + "`=?",
                                                    value)
                                            > 0,
                                    table + "." + field + " missing " + value);
                        }
                    }
                });
    }

    @Test
    void importPreservesExistingRowsAndIsIdempotentAfterUserEdits() {
        rollback(
                () -> {
                    var before = snapshot();
                    populate();
                    for (var entry : before.entrySet()) {
                        var after = db.rows("SELECT * FROM `" + entry.getKey() + "` ORDER BY id");
                        assertTrue(
                                after.containsAll(entry.getValue()),
                                "Existing rows changed in " + entry.getKey());
                    }
                    db.update("UPDATE elder SET name='用户登记姓名' WHERE id=21001");
                    var once = snapshot();
                    populate();
                    assertEquals(once, snapshot());
                });
    }

    @Test
    void lateUniqueConflictRollsBackAllModules() {
        var before = snapshot();
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        new TransactionTemplate(tm)
                                .execute(
                                        status -> {
                                            db.update(
                                                    "INSERT INTO"
                                                        + " metric_rule(metric,low_value,high_value,response_seconds)"
                                                        + " VALUES('血氧',90,100,120)");
                                            populate();
                                            return null;
                                        }));
        assertEquals(before, snapshot());
    }

    @Test
    void historiesAmountsCapacityAndEventTimesAreConsistent() {
        rollback(
                () -> {
                    populate();
                    assertEquals(
                            0,
                            db.count(
                                    "SELECT COUNT(*) FROM service_order o WHERE o.elder_id BETWEEN"
                                        + " 21001 AND 21007 AND o.status='COMPLETED' AND NOT"
                                        + " EXISTS(SELECT 1 FROM dispatch d JOIN attendance a ON"
                                        + " a.dispatch_id=d.id JOIN service_record r ON"
                                        + " r.dispatch_id=d.id WHERE d.order_id=o.id AND"
                                        + " d.status='FINISHED' AND a.check_out IS NOT NULL AND"
                                        + " r.confirmed_by IS NOT NULL AND"
                                        + " r.confirmed_at>=a.check_out)"));
                    assertEquals(
                            0,
                            db.count(
                                    "SELECT COUNT(*) FROM shift s JOIN caregiver c ON"
                                        + " c.id=s.caregiver_id JOIN account a ON a.id=c.account_id"
                                        + " WHERE a.username LIKE 'scene_caregiver%' AND"
                                        + " s.used_count<>(SELECT COUNT(*) FROM dispatch d WHERE"
                                        + " d.shift_id=s.id AND d.status"
                                        + " IN('WAITING','ACCEPTED','FINISHED'))"));
                    assertEquals(
                            0,
                            db.count(
                                    "SELECT COUNT(*) FROM claim c WHERE c.elder_id BETWEEN 21001"
                                            + " AND 21007 AND c.amount<>(SELECT"
                                            + " COALESCE(SUM(i.amount),0) FROM claim_item i WHERE"
                                            + " i.claim_id=c.id)"));
                    assertEquals(
                            0,
                            db.count(
                                    "SELECT COUNT(*) FROM claim_item i JOIN claim c ON"
                                            + " c.id=i.claim_id WHERE c.elder_id BETWEEN 21001 AND"
                                            + " 21007 AND NOT EXISTS(SELECT 1 FROM dispatch d JOIN"
                                            + " attendance a ON a.dispatch_id=d.id WHERE"
                                            + " d.order_id=i.order_id AND a.status='VALID' AND"
                                            + " a.effective_minutes=i.effective_minutes)"));
                    assertEquals(
                            0,
                            db.count(
                                    "SELECT COUNT(*) FROM emergency WHERE elder_id BETWEEN 21001"
                                        + " AND 21007 AND (responded_at<occurred_at OR"
                                        + " arrived_at<responded_at OR closed_at<arrived_at OR"
                                        + " (status='CLOSED' AND (arrived_at IS NULL OR result IS"
                                        + " NULL OR closed_at IS NULL)))"));
                    assertEquals(
                            0,
                            db.count(
                                    "SELECT COUNT(*) FROM service_order o JOIN dispatch d ON"
                                        + " d.order_id=o.id WHERE o.elder_id BETWEEN 21001 AND"
                                        + " 21007 AND (o.created_at>d.assigned_at OR"
                                        + " d.accepted_at<d.assigned_at OR o.created_at>NOW() OR"
                                        + " d.assigned_at>NOW() OR d.accepted_at>NOW())"));
                    assertEquals(
                            0,
                            db.count(
                                    "SELECT COUNT(*) FROM complaint c WHERE c.order_id IN(SELECT id"
                                            + " FROM service_order WHERE elder_id BETWEEN 21001 AND"
                                            + " 21007) AND NOT EXISTS(SELECT 1 FROM complaint_log l"
                                            + " WHERE l.complaint_id=c.id AND l.status=c.status)"));
                });
    }

    @Test
    void examplesSupportRealActionsAndKeepRoleBoundaries() {
        rollback(
                () -> {
                    populate();
                    var family = new Auth.User(2, "family1", "FAMILY", 1, 1L);
                    var admin = new Auth.User(1, "admin1", "ADMIN", 1, 1L);
                    assertDoesNotThrow(() -> reads.details(family, "elders", 21001));
                    assertThrows(Forbidden.class, () -> reads.details(family, "elders", 21005));
                    assertThrows(Forbidden.class, () -> reads.details(admin, "elders", 21005));
                    assertThrows(
                            Forbidden.class,
                            () ->
                                    reads.details(
                                            new Auth.User(6, "regulator1", "REGULATOR", 1, 1L),
                                            "elders",
                                            21005));
                    long crossCommunityClaim =
                            db.count("SELECT id FROM claim WHERE elder_id=21005");
                    assertDoesNotThrow(
                            () ->
                                    reads.details(
                                            new Auth.User(6, "regulator1", "REGULATOR", 1, 1L),
                                            "claims",
                                            crossCommunityClaim));
                    assertThrows(
                            Forbidden.class,
                            () -> reads.details(admin, "claims", crossCommunityClaim));
                    var account = db.one("SELECT * FROM account WHERE username='scene_caregiver1'");
                    var worker =
                            new Auth.User(
                                    Store.id(account, "id"),
                                    "scene_caregiver1",
                                    "CAREGIVER",
                                    1,
                                    1L);
                    long order =
                            db.count(
                                    "SELECT id FROM service_order WHERE elder_id=21002 AND"
                                            + " status='ASSIGNED'");
                    service.execute(worker, "order.accept", Map.of("orderId", order));
                    assertEquals(
                            "ACCEPTED",
                            db.one("SELECT status FROM service_order WHERE id=?", order)
                                    .get("status"));
                    long confirmation =
                            db.count(
                                    "SELECT id FROM service_order WHERE elder_id=21005 AND"
                                            + " status='CONFIRM'");
                    service.execute(
                            new Auth.User(8, "family2", "FAMILY", 2, 2L),
                            "order.confirm",
                            Map.of("orderId", confirmation, "confirmation", "核对服务内容后确认"));
                    assertEquals(
                            "COMPLETED",
                            db.one("SELECT status FROM service_order WHERE id=?", confirmation)
                                    .get("status"));
                    long hours =
                            db.count(
                                    "SELECT a.id FROM attendance a JOIN dispatch d ON"
                                            + " d.id=a.dispatch_id JOIN service_order o ON"
                                            + " o.id=d.order_id WHERE o.elder_id=21001 AND"
                                            + " a.status='REVIEW'");
                    service.execute(
                            new Auth.User(4, "manager1", "MANAGER", 1, 1L),
                            "hours.review",
                            Map.of(
                                    "attendanceId",
                                    hours,
                                    "approve",
                                    false,
                                    "reason",
                                    "核对上门记录后确认工时无效"));
                    assertEquals(
                            "INVALID",
                            db.one("SELECT status FROM attendance WHERE id=?", hours)
                                    .get("status"));
                });
    }

    private Map<String, List<Map<String, Object>>> snapshot() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (var table :
                db.rows(
                        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE"
                                + " TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' ORDER BY"
                                + " TABLE_NAME")) {
            String name = table.get("TABLE_NAME").toString();
            result.put(name, db.rows("SELECT * FROM `" + name + "` ORDER BY id"));
        }
        return result;
    }
}
