package edu.bjfu.eldercare.infrastructure.query;

import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.query.DetailHandler;
import edu.bjfu.eldercare.application.port.query.PageQuery;
import edu.bjfu.eldercare.application.port.query.QueryHandler;
import edu.bjfu.eldercare.application.port.query.QueryModule;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import edu.bjfu.eldercare.infrastructure.security.QueryScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MonitoringQueries implements QueryModule {

    private final Store db;

    private final QueryScope scope;

    public MonitoringQueries(Store db, QueryScope scope) {
        this.db = db;
        this.scope = scope;
    }

    public Map<String, QueryHandler> queries() {
        return Map.ofEntries(
                Map.entry("events", this::events),
                Map.entry("devices", this::devices),
                Map.entry("currentDevices", this::devices),
                Map.entry("vitals", this::vitals),
                Map.entry("abnormalVitals", this::abnormalVitals),
                Map.entry("pendingEvents", this::pendingEvents),
                Map.entry("metrics", this::metrics),
                Map.entry("notifications", this::notifications));
    }

    public Map<String, DetailHandler> details() {
        return Map.ofEntries(
                Map.entry("events", this::eventsDetails),
                Map.entry("devices", this::devicesDetails),
                Map.entry("currentDevices", this::devicesDetails),
                Map.entry("pendingEvents", this::eventsDetails));
    }

    private PageQuery events(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql =
                "SELECT x.*,e.name elder_name,(SELECT el.message FROM emergency_log el WHERE"
                    + " el.event_id=x.id ORDER BY el.id LIMIT 1) report_note FROM emergency x JOIN"
                    + " elder e ON e.id=x.elder_id WHERE "
                        + scope.elderFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery devices(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "FAMILY", "CAREGIVER");
        if (user.role().equals("ADMIN")) {
            sql =
                    "SELECT d.*,b.id binding_id,b.elder_id FROM device d LEFT JOIN device_binding b"
                        + " ON b.device_id=d.id AND b.unbound_at IS NULL WHERE d.community_id=?";
            parameters.add(user.community());
        } else {
            sql =
                    "SELECT d.*,b.id binding_id,b.elder_id FROM device d JOIN device_binding b ON"
                            + " b.device_id=d.id AND b.unbound_at IS NULL JOIN elder e ON"
                            + " e.id=b.elder_id WHERE "
                            + scope.elderFilter(user, parameters);
        }
        return new PageQuery(sql, parameters);
    }

    private PageQuery vitals(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "FAMILY", "CAREGIVER");
        sql =
                "SELECT v.*,b.elder_id FROM vital v JOIN device_binding b ON b.id=v.binding_id JOIN"
                        + " elder e ON e.id=b.elder_id WHERE "
                        + scope.elderFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery abnormalVitals(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "FAMILY");
        sql =
                "SELECT v.* FROM v_abnormal_vital v JOIN elder e ON e.id=v.elder_id WHERE "
                        + scope.elderFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery pendingEvents(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "REGULATOR");
        sql =
                "SELECT * FROM v_emergency_pending WHERE "
                        + scope.communityFilter(user, "community_id", parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery metrics(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN");
        sql = "SELECT * FROM metric_rule";
        return new PageQuery(sql, parameters);
    }

    private PageQuery notifications(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql =
                "SELECT n.* FROM notification n WHERE n.account_id=? AND (n.assessment_id IS NULL"
                        + " OR EXISTS(SELECT 1 FROM v_assessment_due a WHERE"
                        + " a.assessment_id=n.assessment_id))";
        parameters.add(user.id());
        return new PageQuery(sql, parameters);
    }

    private Object eventsDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        var e = db.one("SELECT * FROM emergency WHERE id=?", recordId);
        scope.elder(user, id(e, "elder_id"));
        out.put("应急事件", e);
        out.put(
                "升级记录",
                db.rows(
                        "SELECT level,created_at,message FROM emergency_log WHERE event_id=? ORDER"
                                + " BY id",
                        recordId));
        out.put(
                "处置记录",
                db.rows(
                        "SELECT s.*,a.username actor_name FROM event_step s JOIN account a ON"
                                + " a.id=s.actor_id WHERE s.event_id=? ORDER BY s.id",
                        recordId));
        return out;
    }

    private Object devicesDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        Auth.role(user, "ADMIN", "FAMILY", "CAREGIVER");
        var device = db.one("SELECT * FROM device WHERE id=?", recordId);
        if (user.role().equals("ADMIN")) {
            scope.community(user, id(device, "community_id"));
            out.put(
                    "绑定历史",
                    db.rows(
                            "SELECT b.*,e.name elder_name FROM device_binding b JOIN elder e ON"
                                    + " e.id=b.elder_id WHERE b.device_id=? ORDER BY b.id",
                            recordId));
        } else {
            List<Object> parameters = new ArrayList<>(List.of(recordId));
            String filter = scope.elderFilter(user, parameters);
            var bindings =
                    db.rows(
                            "SELECT b.* FROM device_binding b JOIN elder e ON e.id=b.elder_id WHERE"
                                    + " b.device_id=? AND "
                                    + filter,
                            parameters.toArray());
            if (bindings.isEmpty()) {
                throw new Forbidden();
            }
            out.put("本人的绑定历史", bindings);
        }
        out.put("设备", device);
        return out;
    }
}
