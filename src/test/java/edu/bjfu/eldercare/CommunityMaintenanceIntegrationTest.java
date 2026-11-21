package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.jobs=false")
@Transactional
class CommunityMaintenanceIntegrationTest {
    @Autowired Store db;
    @Autowired CareService service;
    final Auth.User admin = WorkflowIntegrationTest.u(1, "ADMIN");

    @Test
    void manualExpiryPausesOnlyLocalPlansAndTheSchedulerStillCoversAllCommunities() {
        assertEquals("ACTIVE", planStatus(1));
        assertEquals("ACTIVE", planStatus(2));
        long afterExpiry =
                db.count(
                        "SELECT UNIX_TIMESTAMP(MAX(valid_until))+1 FROM assessment WHERE id IN"
                                + " (1,2)");
        db.update("SET timestamp=?", afterExpiry);
        try {
            service.execute(admin, "maintenance.run", Map.of());
            assertEquals("PAUSED", planStatus(1));
            assertEquals("ACTIVE", planStatus(2));
            db.call("expire_plans");
            assertEquals("PAUSED", planStatus(2));
        } finally {
            db.update("SET timestamp=0");
        }
    }

    private String planStatus(long id) {
        return (String) db.one("SELECT status FROM care_plan WHERE id=?", id).get("status");
    }

    @Test
    void manualEscalationUpdatesOnlyLocalEventsLogsAndNotices() {
        long neighborAdmin = WorkflowIntegrationTest.u(2, "ADMIN").id();
        db.update("UPDATE account SET active=0 WHERE id=?", neighborAdmin);
        long local = overdueEvent(1), neighbor = overdueEvent(2);
        db.update("UPDATE account SET active=1 WHERE id=?", neighborAdmin);
        service.execute(admin, "maintenance.run", Map.of());
        service.execute(admin, "maintenance.run", Map.of());
        assertEquals(2L, db.count("SELECT level FROM emergency WHERE id=?", local));
        assertEquals(1L, db.count("SELECT level FROM emergency WHERE id=?", neighbor));
        assertEquals(
                1L,
                db.count("SELECT COUNT(*) FROM emergency_log WHERE event_id=? AND level=2", local));
        assertEquals(0L, db.count("SELECT COUNT(*) FROM emergency_log WHERE event_id=?", neighbor));
        assertTrue(levelTwoNotices(local) > 0);
        assertEquals(0L, levelTwoNotices(neighbor));
        assertEquals(0L, noticesFor(neighbor, neighborAdmin));

        db.call("escalate_events");
        assertEquals(2L, db.count("SELECT level FROM emergency WHERE id=?", neighbor));
        assertTrue(levelTwoNotices(neighbor) > 0);
        assertEquals(1L, noticesFor(neighbor, neighborAdmin));
    }

    private long noticesFor(long eventId, long accountId) {
        return db.count(
                "SELECT COUNT(*) FROM notification WHERE event_id=? AND account_id=?",
                eventId,
                accountId);
    }

    private long overdueEvent(long elderId) {
        return db.insert(
                "INSERT INTO emergency(elder_id,source,deadline,status)"
                        + " VALUES(?,'MANUAL',NOW()-INTERVAL 1 SECOND,'OPEN')",
                elderId);
    }

    private long levelTwoNotices(long eventId) {
        return db.count(
                "SELECT COUNT(*) FROM notification WHERE event_id=? AND message='应急事件待处理，级别 2'",
                eventId);
    }

    @Test
    void manualSamplingSkipsAnInactiveElderWithAnExistingBinding() {
        long localBefore = samples(1), otherBefore = samples(2);
        service.execute(admin, "simulator.run", Map.of("metricId", 1));
        assertEquals(localBefore + 1, samples(1));
        assertEquals(otherBefore, samples(2));
        db.update("UPDATE elder SET status='INACTIVE' WHERE id=1");
        service.execute(admin, "simulator.run", Map.of("metricId", 1));
        assertEquals(localBefore + 1, samples(1));
        assertEquals(otherBefore, samples(2));
    }

    private long samples(long elderId) {
        return db.count(
                "SELECT COUNT(*) FROM vital v JOIN device_binding b ON b.id=v.binding_id WHERE"
                        + " b.elder_id=?",
                elderId);
    }
}
