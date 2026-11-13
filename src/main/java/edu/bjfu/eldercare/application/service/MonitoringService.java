package edu.bjfu.eldercare.application.service;

import static edu.bjfu.eldercare.application.support.Inputs.choice;
import static edu.bjfu.eldercare.application.support.Inputs.decimal;
import static edu.bjfu.eldercare.application.support.Inputs.flag;
import static edu.bjfu.eldercare.application.support.Inputs.number;
import static edu.bjfu.eldercare.application.support.Inputs.rawNumber;
import static edu.bjfu.eldercare.application.support.Inputs.rawText;
import static edu.bjfu.eldercare.application.support.Inputs.text;
import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.ActionHandler;
import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.RecordAccess;
import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.domain.monitoring.EmergencyEvent;
import edu.bjfu.eldercare.domain.repository.MonitoringRepository;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 设备、体征采样与应急处置。 */
@Component
public class MonitoringService implements ActionModule {

    private final MonitoringRepository repository;

    private final Scope scope;

    private final RecordAccess access;

    public MonitoringService(MonitoringRepository repository, Scope scope, RecordAccess access) {
        this.repository = repository;
        this.scope = scope;
        this.access = access;
    }

    public Map<String, ActionHandler> actions() {
        return Map.ofEntries(
                Map.entry("device.create", this::createDevice),
                Map.entry("device.bind", this::bindDevice),
                Map.entry("vital.upload", this::uploadVital),
                Map.entry("event.create", this::createEvent),
                Map.entry("event.respond", this::respondEvent),
                Map.entry("event.close", this::closeEvent),
                Map.entry("maintenance.run", this::runMaintenance),
                Map.entry("metric.create", this::createMetric),
                Map.entry("device.unbind", this::unbindDevice),
                Map.entry("device.status", this::changeDeviceStatus),
                Map.entry("device.simulation", this::configureDeviceSimulation),
                Map.entry("simulator.run", this::runSimulator),
                Map.entry("event.arrive", this::arriveEvent),
                Map.entry("event.confirm", this::confirmEvent),
                Map.entry("event.dismiss", this::dismissEvent));
    }

    private Object createDevice(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        return repository.insertDevice(
                text(input, "serial"),
                text(input, "type"),
                text(input, "location"),
                user.community());
    }

    private Object bindDevice(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = rawNumber(input, "elderId");
        access.elder(user, recordId);
        long device = rawNumber(input, "deviceId");
        var dev = repository.findDeviceForUpdate(device);
        scope.community(user, id(dev, "community_id"));
        var bindings = repository.findActiveBindings(device);
        for (var z : bindings) {
            access.elder(user, id(z, "elder_id"));
        }
        repository.unbindDevice(device);
        return repository.bindDevice(device, recordId);
    }

    private Object uploadVital(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        var samples = input.get("samples");
        if (!(samples instanceof List<?> list) || list.isEmpty() || list.size() > 500) {
            throw new Rule("samples 必须为包含 1 至 500 条的数组");
        }
        for (Object v : list) {
            if (!(v instanceof Map<?, ?>)) {
                throw new Rule("每条采样必须是对象");
            }
            @SuppressWarnings("unchecked")
            var z = (Map<String, Object>) v;
            var bind = repository.findBinding(rawNumber(z, "bindingId"));
            access.elder(user, id(bind, "elder_id"));
            repository.insertVital(
                    rawNumber(z, "bindingId"),
                    rawNumber(z, "metricId"),
                    rawText(z, "sampleKey"),
                    rawText(z, "collectedAt"),
                    rawText(z, "value"),
                    "SIMULATOR");
        }
        return list.size();
    }

    private Object createEvent(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN", "FAMILY", "CAREGIVER");
        long recordId = rawNumber(input, "elderId");
        access.elder(user, recordId);
        long level = rawNumber(input, "level");
        if (level < 1 || level > 3) {
            throw new Rule("事件等级应为 1 至 3");
        }
        String source = user.role().equals("FAMILY") ? "FAMILY_CALL" : "MANUAL";
        long eventId =
                repository.insertEmergency(
                        recordId, source, level, level == 1 ? 300 : level == 2 ? 180 : 60);
        repository.appendEmergencyNotification(eventId, level, rawText(input, "description"));
        return eventId;
    }

    private Object respondEvent(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN", "CAREGIVER");
        long id = number(input, "eventId");
        var e = event(user, id);
        eventState(e).requireRespondable();
        repository.respondToEmergency(user.id(), id);
        step(user, id, "RESPOND", "已接受处置任务");
        return "事件记录已更新";
    }

    private Object closeEvent(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN", "CAREGIVER");
        long id = number(input, "eventId");
        var e = event(user, id);
        eventState(e).requireClosable();
        responder(user, e);
        repository.closeEmergency(text(input, "result"), id);
        step(user, id, "CLOSE", text(input, "result"));
        return "事件记录已更新";
    }

    private Object runMaintenance(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        repository.expirePlans();
        repository.escalateEvents();
        return "到期及超时检查完成";
    }

    private Object createMetric(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        return repository.insertMetric(
                text(input, "name"),
                decimal(input, "low"),
                decimal(input, "high"),
                number(input, "seconds"));
    }

    private Object unbindDevice(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "deviceId");
        Map<String, Object> record = repository.findDeviceForUpdate(recordId);
        scope.community(user, id(record, "community_id"));
        return repository.unbindDevice(recordId);
    }

    private Object changeDeviceStatus(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "deviceId");
        Map<String, Object> record = repository.findDeviceForUpdate(recordId);
        scope.community(user, id(record, "community_id"));
        String state = choice(input, "status", "ACTIVE", "OFFLINE", "RETIRED");
        if (state.equals("RETIRED") && repository.countActiveBindings(recordId) > 0) {
            throw new Rule("请先解绑设备");
        }
        return repository.updateDeviceStatus(state, recordId);
    }

    private Object configureDeviceSimulation(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "deviceId");
        Map<String, Object> record = repository.findDeviceForUpdate(recordId);
        scope.community(user, id(record, "community_id"));
        boolean enabled = flag(input, "enabled");
        long metric = number(input, "metricId"), seconds = number(input, "seconds");
        repository.findMetric(metric);
        if (seconds < 30 || seconds > 3600) {
            throw new Rule("采样间隔应为 30 至 3600 秒");
        }
        if (enabled
                && (!record.get("status").equals("ACTIVE")
                        || repository.countActiveBindings(recordId) == 0)) {
            throw new Rule("先启用并绑定设备");
        }
        return repository.configureSimulation(enabled, metric, seconds, recordId);
    }

    private Object runSimulator(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        repository.findMetric(number(input, "metricId"));
        int written = 0;
        for (var sample : repository.findSimulationBindings(user.community())) {
            repository.insertSimulatedVital(
                    (Number) sample.get("id"),
                    UUID.randomUUID().toString(),
                    number(input, "metricId"));
            written++;
        }
        return written;
    }

    private Object arriveEvent(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN", "CAREGIVER");
        long id = number(input, "eventId");
        var e = event(user, id);
        eventState(e).requireArrivalRecordable();
        responder(user, e);
        repository.recordArrival(id);
        step(user, id, "ARRIVE", text(input, "note"));
        return "事件记录已更新";
    }

    private Object confirmEvent(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN", "CAREGIVER");
        long id = number(input, "eventId");
        var e = event(user, id);
        eventState(e).requireConfirmable();
        repository.confirmEmergency(id);
        step(user, id, "CONFIRM", text(input, "reason"));
        return "事件记录已更新";
    }

    private Object dismissEvent(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN", "CAREGIVER");
        long id = number(input, "eventId");
        var e = event(user, id);
        Auth.role(user, "ADMIN");
        eventState(e).requireDismissible();
        repository.dismissEmergency(text(input, "reason"), id);
        step(user, id, "FALSE_ALARM", text(input, "reason"));
        return "事件记录已更新";
    }

    private EmergencyEvent eventState(Map<String, Object> row) {
        return new EmergencyEvent(
                id(row, "id"),
                EmergencyEvent.Status.valueOf((String) row.get("status")),
                row.get("arrived_at") != null);
    }

    private void step(Auth.User user, long event, String action, String message) {
        repository.appendEventStep(event, user.id(), action, message);
    }

    private Map<String, Object> event(Auth.User user, long event) {
        var e = repository.findEmergencyForUpdate(event);
        scope.elder(user, id(e, "elder_id"));
        return e;
    }

    private void responder(Auth.User user, Map<String, Object> e) {
        if (user.role().equals("CAREGIVER") && !Objects.equals(e.get("responded_by"), user.id())) {
            throw new Forbidden();
        }
    }
}
