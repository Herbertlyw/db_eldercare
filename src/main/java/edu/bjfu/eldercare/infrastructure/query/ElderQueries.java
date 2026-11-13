package edu.bjfu.eldercare.infrastructure.query;

import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.Crypto;
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
import org.springframework.stereotype.Component;

@Component
public class ElderQueries implements QueryModule {

    private final Store db;

    private final QueryScope scope;

    private final Crypto crypto;

    public ElderQueries(Store db, QueryScope scope, Crypto crypto) {
        this.db = db;
        this.scope = scope;
        this.crypto = crypto;
    }

    public Map<String, QueryHandler> queries() {
        return Map.ofEntries(
                Map.entry("elders", this::elders),
                Map.entry("plans", this::plans),
                Map.entry("assessments", this::assessments),
                Map.entry("assessmentDue", this::assessmentDue),
                Map.entry("activePlans", this::activePlans),
                Map.entry("familyCare", this::familyCare),
                Map.entry("catalog", this::catalog),
                Map.entry("bundles", this::bundles),
                Map.entry("accounts", this::accounts));
    }

    public Map<String, DetailHandler> details() {
        return Map.ofEntries(
                Map.entry("elders", this::eldersDetails),
                Map.entry("plans", this::plansDetails),
                Map.entry("bundles", this::bundlesDetails));
    }

    private PageQuery elders(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql =
                "SELECT"
                    + " e.id,e.name,e.birth_date,e.address,e.community_id,e.status,e.position_x,e.position_y,(SELECT"
                    + " GROUP_CONCAT(tag ORDER BY tag) FROM elder_tag t WHERE t.elder_id=e.id) tags"
                    + " FROM elder e WHERE "
                        + scope.elderFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery plans(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql =
                "SELECT p.*,e.name elder_name FROM v_plan_context p JOIN elder e ON e.id=p.elder_id"
                        + " WHERE "
                        + scope.elderFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery assessments(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql =
                "SELECT a.*,e.name elder_name FROM assessment a JOIN elder e ON e.id=a.elder_id"
                        + " WHERE "
                        + scope.elderFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery assessmentDue(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN");
        sql = "SELECT * FROM v_assessment_due WHERE community_id=?";
        parameters.add(user.community());
        return new PageQuery(sql, parameters);
    }

    private PageQuery activePlans(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN");
        sql = "SELECT * FROM v_active_plan WHERE community_id=?";
        parameters.add(user.community());
        return new PageQuery(sql, parameters);
    }

    private PageQuery familyCare(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "FAMILY");
        sql = "SELECT * FROM v_family_care WHERE account_id=?";
        parameters.add(user.id());
        return new PageQuery(sql, parameters);
    }

    private PageQuery catalog(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql = "SELECT * FROM service_item WHERE 1=1";
        return new PageQuery(sql, parameters);
    }

    private PageQuery bundles(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql = "SELECT * FROM care_bundle WHERE 1=1";
        return new PageQuery(sql, parameters);
    }

    private PageQuery accounts(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN");
        sql = "SELECT id,username,role,active FROM account WHERE community_id=? AND role='FAMILY'";
        parameters.add(user.community());
        return new PageQuery(sql, parameters);
    }

    private Object eldersDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        Auth.role(user, "ADMIN", "FAMILY", "CAREGIVER");
        scope.elder(user, recordId);
        out.put(
                "档案",
                db.one(
                        "SELECT"
                            + " id,name,birth_date,address,community_id,status,position_x,position_y"
                            + " FROM elder WHERE id=?",
                        recordId));
        out.put("标签", db.rows("SELECT id,tag FROM elder_tag WHERE elder_id=?", recordId));
        var guardians =
                db.rows(
                        "SELECT id,relation_name,phone_cipher,active FROM guardian WHERE"
                                + " elder_id=?",
                        recordId);
        for (var g : guardians) {
            g.put("phone", crypto.decrypt((String) g.remove("phone_cipher")));
        }
        out.put("家属联系方式", guardians);
        return out;
    }

    private Object plansDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        var plan =
                db.one(
                        "SELECT p.*,b.name bundle_name,c.name caregiver_name FROM v_plan_context p"
                                + " JOIN care_bundle b ON b.id=p.bundle_id JOIN caregiver c ON"
                                + " c.id=p.caregiver_id WHERE p.id=?",
                        recordId);
        scope.elder(user, id(plan, "elder_id"));
        out.put("照护计划", plan);
        out.put(
                "服务项目与频次",
                db.rows(
                        "SELECT i.id,i.item_id,s.name,i.weekly_frequency FROM plan_item i JOIN"
                                + " service_item s ON s.id=i.item_id WHERE i.plan_id=?",
                        recordId));
        return out;
    }

    private Object bundlesDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("服务包", db.one("SELECT * FROM care_bundle WHERE id=?", recordId));
        out.put(
                "项目清单",
                db.rows(
                        "SELECT i.*,s.name FROM bundle_item i JOIN service_item s ON s.id=i.item_id"
                                + " WHERE i.bundle_id=?",
                        recordId));
        return out;
    }
}
