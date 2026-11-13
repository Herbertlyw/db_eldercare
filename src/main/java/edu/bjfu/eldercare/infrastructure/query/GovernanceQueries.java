package edu.bjfu.eldercare.infrastructure.query;

import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.query.DetailHandler;
import edu.bjfu.eldercare.application.port.query.PageQuery;
import edu.bjfu.eldercare.application.port.query.QueryHandler;
import edu.bjfu.eldercare.application.port.query.QueryModule;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import edu.bjfu.eldercare.infrastructure.security.QueryScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GovernanceQueries implements QueryModule {

    private final Store db;

    private final QueryScope scope;

    public GovernanceQueries(Store db, QueryScope scope) {
        this.db = db;
        this.scope = scope;
    }

    public Map<String, QueryHandler> queries() {
        return Map.ofEntries(
                Map.entry("inspections", this::inspections),
                Map.entry("rectifications", this::rectifications),
                Map.entry("organizationQuality", this::organizationQuality),
                Map.entry("inspectionBatches", this::inspectionBatches),
                Map.entry("organizationReports", this::organizationReports),
                Map.entry("publicNotices", this::publicNotices),
                Map.entry("jurisdictionStats", this::jurisdictionStats),
                Map.entry("communities", this::communities));
    }

    public Map<String, DetailHandler> details() {
        return Map.ofEntries(
                Map.entry("inspections", this::inspectionsDetails),
                Map.entry("rectifications", this::inspectionsDetails),
                Map.entry("inspectionBatches", this::inspectionBatchesDetails));
    }

    private PageQuery inspections(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "MANAGER", "REGULATOR");
        sql =
                "SELECT i.* FROM inspection i LEFT JOIN service_order o ON o.id=i.order_id LEFT"
                        + " JOIN caregiver c ON c.id=i.caregiver_id LEFT JOIN organization org ON"
                        + " org.id=c.organization_id WHERE ";
        if (user.role().equals("MANAGER")) {
            parameters.add(user.organization());
            parameters.add(user.organization());
            sql +=
                    "(c.organization_id=? OR EXISTS(SELECT 1 FROM v_dispatch_context d JOIN"
                            + " caregiver w ON w.id=d.caregiver_id WHERE d.order_id=i.order_id AND"
                            + " d.status='FINISHED' AND w.organization_id=?))";
        } else {
            sql +=
                    scope.communityFilter(
                            user, "COALESCE(o.community_id,org.community_id)", parameters);
        }
        return new PageQuery(sql, parameters);
    }

    private PageQuery rectifications(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "REGULATOR");
        sql =
                "SELECT * FROM v_rectification WHERE "
                        + scope.communityFilter(user, "community_id", parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery organizationQuality(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "MANAGER", "REGULATOR");
        sql =
                "SELECT org.id,org.name,(SELECT ROUND(AVG(i.score),2) FROM inspection i WHERE"
                    + " i.caregiver_id IN(SELECT id FROM caregiver WHERE organization_id=org.id) OR"
                    + " EXISTS(SELECT 1 FROM v_dispatch_context d JOIN caregiver c ON"
                    + " c.id=d.caregiver_id WHERE d.order_id=i.order_id AND d.status='FINISHED' AND"
                    + " c.organization_id=org.id)) inspection_score FROM organization org WHERE "
                        + scope.organizationFilter(user, parameters, "org.id");
        return new PageQuery(sql, parameters);
    }

    private PageQuery inspectionBatches(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "REGULATOR");
        sql =
                "SELECT * FROM inspection_batch WHERE "
                        + scope.communityFilter(user, "community_id", parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery organizationReports(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "MANAGER", "REGULATOR");
        sql =
                "SELECT r.* FROM organization_report r WHERE "
                        + scope.organizationFilter(user, parameters, "r.organization_id");
        return new PageQuery(sql, parameters);
    }

    private PageQuery publicNotices(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql = "SELECT id,title,body,published_at FROM public_notice WHERE withdrawn_at IS NULL";
        return new PageQuery(sql, parameters);
    }

    private PageQuery jurisdictionStats(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "STREET", "REGULATOR");
        sql = stats(user, parameters);
        return new PageQuery(sql, parameters, this::addRates);
    }

    private PageQuery communities(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql = "SELECT * FROM community WHERE ";
        if (Set.of("STREET", "REGULATOR").contains(user.role())) {
            sql += scope.communityFilter(user, "id", parameters);
        } else {
            sql += "1=1";
        }
        return new PageQuery(sql, parameters);
    }

    private Object inspectionsDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        Auth.role(user, "ADMIN", "MANAGER", "REGULATOR");
        out.put("质量抽查", scope.inspection(user, recordId));
        out.put(
                "整改与复核",
                db.rows(
                        "SELECT * FROM rectification_submission WHERE inspection_id=? ORDER BY id",
                        recordId));
        return out;
    }

    private Object inspectionBatchesDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        Auth.role(user, "REGULATOR");
        var batch = db.one("SELECT * FROM inspection_batch WHERE id=?", recordId);
        scope.community(user, id(batch, "community_id"));
        out.put("抽样依据", batch);
        out.put("抽中对象", db.rows("SELECT * FROM inspection WHERE batch_id=? ORDER BY id", recordId));
        return out;
    }

    private String stats(Auth.User user, List<Object> parameters) {
        return "SELECT co.id,co.name,co.district,co.street_name,DATE_FORMAT(CURRENT_DATE,'%Y-%m')"
                + " period,(SELECT COUNT(*) FROM elder e WHERE e.community_id=co.id AND"
                + " e.status='ACTIVE') active_elders,(SELECT COUNT(DISTINCT e.id) FROM elder e"
                + " JOIN service_order o ON o.elder_id=e.id WHERE e.community_id=co.id AND"
                + " e.status='ACTIVE' AND o.status='COMPLETED' AND"
                + " DATE_FORMAT(o.starts_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m'))"
                + " served_elders,(SELECT COUNT(*) FROM emergency x JOIN elder e ON"
                + " e.id=x.elder_id WHERE e.community_id=co.id AND"
                + " DATE_FORMAT(x.occurred_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m'))"
                + " events_total,(SELECT COUNT(*) FROM emergency x JOIN elder e ON"
                + " e.id=x.elder_id WHERE e.community_id=co.id AND x.status<>'FALSE_ALARM' AND"
                + " x.initial_deadline IS NOT NULL AND"
                + " DATE_FORMAT(x.occurred_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m'))"
                + " response_denominator,(SELECT COUNT(*) FROM emergency x JOIN elder e ON"
                + " e.id=x.elder_id WHERE e.community_id=co.id AND x.status<>'FALSE_ALARM' AND"
                + " x.responded_at<=x.initial_deadline AND"
                + " DATE_FORMAT(x.occurred_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m'))"
                + " timely_responses,(SELECT COALESCE(SUM(c.amount),0) FROM claim c JOIN payment"
                + " pay ON pay.claim_id=c.id WHERE c.community_id=co.id AND c.status='PAID' AND"
                + " DATE_FORMAT(pay.paid_at,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m'))"
                + " paid_subsidy FROM community co WHERE "
                + scope.communityFilter(user, "co.id", parameters);
    }

    private Map<String, Object> addRates(Map<String, Object> record) {
        long elders = id(record, "active_elders"), events = id(record, "response_denominator");
        record.put(
                "coverage_rate",
                elders == 0
                        ? null
                        : Math.round(id(record, "served_elders") * 10000.0 / elders) / 100.0);
        record.put(
                "response_rate",
                events == 0
                        ? null
                        : Math.round(id(record, "timely_responses") * 10000.0 / events) / 100.0);
        return record;
    }
}
