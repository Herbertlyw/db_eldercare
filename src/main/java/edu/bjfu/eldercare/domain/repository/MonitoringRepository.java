package edu.bjfu.eldercare.domain.repository;

import java.util.List;
import java.util.Map;

/** 业务用例所需的数据操作契约；SQL 与 JDBC 由基础设施实现。 */
public interface MonitoringRepository {

    long insertDevice(String serialNo, String typeName, String location, Number communityId);

    Map<String, Object> findDeviceForUpdate(Number deviceId);

    List<Map<String, Object>> findActiveBindings(Number deviceId);

    int unbindDevice(Number deviceId);

    long bindDevice(Number deviceId, Number elderId);

    Map<String, Object> findBinding(Number bindingId);

    long insertVital(
            Number bindingId,
            Number metricId,
            String sampleKey,
            String collectedAt,
            String value,
            String source);

    long insertEmergency(Number elderId, String source, Number level, Number responseSeconds);

    long appendEmergencyNotification(Number eventId, Number level, String message);

    int respondToEmergency(Number responderId, Number eventId);

    int closeEmergency(String result, Number eventId);

    void expirePlans();

    void escalateEvents();

    long insertMetric(String metric, Number lowValue, Number highValue, Number responseSeconds);

    long countActiveBindings(Number deviceId);

    int updateDeviceStatus(String status, Number deviceId);

    Map<String, Object> findMetric(Number metricId);

    int configureSimulation(
            Boolean enabled, Number metricId, Number intervalSeconds, Number deviceId);

    List<Map<String, Object>> findSimulationBindings(Number communityId);

    long insertSimulatedVital(Number bindingId, String sampleKey, Number metricId);

    int recordArrival(Number eventId);

    int confirmEmergency(Number eventId);

    int dismissEmergency(String reason, Number eventId);

    long appendEventStep(Number eventId, Number actorId, String action, String message);

    Map<String, Object> findEmergencyForUpdate(Number eventId);
}
