package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.domain.repository.MonitoringRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMonitoringRepository implements MonitoringRepository {

    private final Store db;

    public JdbcMonitoringRepository(Store db) {
        this.db = db;
    }

    @Override
    public long insertDevice(
            String serialNo, String typeName, String location, Number communityId) {
        return db.insert(
                "INSERT INTO device(serial_no,type_name,location_note,community_id)"
                        + " VALUES(?,?,?,?)",
                serialNo,
                typeName,
                location,
                communityId);
    }

    @Override
    public Map<String, Object> findDeviceForUpdate(Number deviceId) {
        return db.one("SELECT * FROM device WHERE id=? FOR UPDATE", deviceId);
    }

    @Override
    public List<Map<String, Object>> findActiveBindings(Number deviceId) {
        return db.rows(
                "SELECT * FROM device_binding WHERE device_id=? AND unbound_at IS NULL", deviceId);
    }

    @Override
    public int unbindDevice(Number deviceId) {
        return db.update(
                "UPDATE device_binding SET unbound_at=NOW(6) WHERE device_id=? AND unbound_at IS"
                        + " NULL",
                deviceId);
    }

    @Override
    public long bindDevice(Number deviceId, Number elderId) {
        return db.insert(
                "INSERT INTO device_binding(device_id,elder_id,bound_at) VALUES(?,?,NOW(6))",
                deviceId,
                elderId);
    }

    @Override
    public Map<String, Object> findBinding(Number bindingId) {
        return db.one("SELECT * FROM device_binding WHERE id=?", bindingId);
    }

    @Override
    public long insertVital(
            Number bindingId,
            Number metricId,
            String sampleKey,
            String collectedAt,
            String value,
            String source) {
        return db.insert(
                "INSERT INTO"
                    + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                    + " VALUES(?,?,?,?,?,?)",
                bindingId,
                metricId,
                sampleKey,
                collectedAt,
                value,
                source);
    }

    @Override
    public long insertEmergency(
            Number elderId, String source, Number level, Number responseSeconds) {
        return db.insert(
                "INSERT INTO emergency(elder_id,source,level,deadline,status)"
                        + " VALUES(?,?,?,DATE_ADD(NOW(),INTERVAL ? SECOND),'OPEN')",
                elderId,
                source,
                level,
                responseSeconds);
    }

    @Override
    public long appendEmergencyNotification(Number eventId, Number level, String message) {
        return db.insert(
                "INSERT INTO emergency_log(event_id,level,message) VALUES(?,?,?)",
                eventId,
                level,
                message);
    }

    @Override
    public int respondToEmergency(Number responderId, Number eventId) {
        return db.update(
                "UPDATE emergency SET status='RESPONDED',responded_by=?,responded_at=NOW() WHERE"
                        + " id=?",
                responderId,
                eventId);
    }

    @Override
    public int closeEmergency(String result, Number eventId) {
        return db.update(
                "UPDATE emergency SET status='CLOSED',closed_at=NOW(6),result=? WHERE id=?",
                result,
                eventId);
    }

    @Override
    public void expirePlans() {
        db.call("expire_plans");
    }

    @Override
    public void escalateEvents() {
        db.call("escalate_events");
    }

    @Override
    public long insertMetric(
            String metric, Number lowValue, Number highValue, Number responseSeconds) {
        return db.insert(
                "INSERT INTO metric_rule(metric,low_value,high_value,response_seconds)"
                        + " VALUES(?,?,?,?)",
                metric,
                lowValue,
                highValue,
                responseSeconds);
    }

    @Override
    public long countActiveBindings(Number deviceId) {
        return db.count(
                "SELECT COUNT(*) FROM device_binding WHERE device_id=? AND unbound_at IS NULL",
                deviceId);
    }

    @Override
    public int updateDeviceStatus(String status, Number deviceId) {
        return db.update("UPDATE device SET status=? WHERE id=?", status, deviceId);
    }

    @Override
    public Map<String, Object> findMetric(Number metricId) {
        return db.one("SELECT id FROM metric_rule WHERE id=?", metricId);
    }

    @Override
    public int configureSimulation(
            Boolean enabled, Number metricId, Number intervalSeconds, Number deviceId) {
        return db.update(
                "UPDATE device SET"
                    + " simulation_enabled=?,simulation_metric_id=?,simulation_seconds=?,last_simulated_at=NULL"
                    + " WHERE id=?",
                enabled,
                metricId,
                intervalSeconds,
                deviceId);
    }

    @Override
    public List<Map<String, Object>> findSimulationBindings(Number communityId) {
        return db.rows(
                "SELECT b.id FROM device_binding b JOIN device d ON d.id=b.device_id JOIN elder e"
                        + " ON e.id=b.elder_id WHERE d.status='ACTIVE' AND e.community_id=? AND"
                        + " b.unbound_at IS NULL",
                communityId);
    }

    @Override
    public long insertSimulatedVital(Number bindingId, String sampleKey, Number metricId) {
        return db.insert(
                "INSERT INTO"
                    + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                    + " SELECT ?,id,?,NOW(6),(low_value+high_value)/2,'SIMULATOR' FROM metric_rule"
                    + " WHERE id=?",
                bindingId,
                sampleKey,
                metricId);
    }

    @Override
    public int recordArrival(Number eventId) {
        return db.update("UPDATE emergency SET arrived_at=NOW() WHERE id=?", eventId);
    }

    @Override
    public int confirmEmergency(Number eventId) {
        return db.update("UPDATE emergency SET status='OPEN' WHERE id=?", eventId);
    }

    @Override
    public int dismissEmergency(String reason, Number eventId) {
        return db.update(
                "UPDATE emergency SET status='FALSE_ALARM',closed_at=NOW(6),close_reason=? WHERE"
                        + " id=?",
                reason,
                eventId);
    }

    @Override
    public long appendEventStep(Number eventId, Number actorId, String action, String message) {
        return db.insert(
                "INSERT INTO event_step(event_id,actor_id,action,message) VALUES(?,?,?,?)",
                eventId,
                actorId,
                action,
                message);
    }

    @Override
    public Map<String, Object> findEmergencyForUpdate(Number eventId) {
        return db.one("SELECT * FROM emergency WHERE id=? FOR UPDATE", eventId);
    }
}
