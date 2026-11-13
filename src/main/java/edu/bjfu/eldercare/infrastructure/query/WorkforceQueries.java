package edu.bjfu.eldercare.infrastructure.query;

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
public class WorkforceQueries implements QueryModule {

    private final Store db;

    private final QueryScope scope;

    public WorkforceQueries(Store db, QueryScope scope) {
        this.db = db;
        this.scope = scope;
    }

    public Map<String, QueryHandler> queries() {
        return Map.ofEntries(
                Map.entry("caregivers", this::caregivers),
                Map.entry("shifts", this::shifts),
                Map.entry("credentials", this::credentials),
                Map.entry("hours", this::hours),
                Map.entry("credentialDue", this::credentialDue),
                Map.entry("shiftCapacity", this::shiftCapacity),
                Map.entry("hoursReview", this::hoursReview),
                Map.entry("monthlyHours", this::monthlyHours),
                Map.entry("quality", this::quality),
                Map.entry("recruitments", this::recruitments),
                Map.entry("trainings", this::trainings),
                Map.entry("trainingRecords", this::trainingRecords),
                Map.entry("areas", this::areas));
    }

    public Map<String, DetailHandler> details() {
        return Map.ofEntries(Map.entry("caregivers", this::caregiversDetails));
    }

    private PageQuery caregivers(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "MANAGER", "CAREGIVER", "REGULATOR");
        sql =
                "SELECT c.*,a.community_id FROM caregiver c JOIN account a ON a.id=c.account_id"
                        + " WHERE "
                        + scope.workerFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery shifts(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "MANAGER", "CAREGIVER");
        sql =
                "SELECT s.*,c.name caregiver_name FROM shift s JOIN caregiver c ON"
                        + " c.id=s.caregiver_id JOIN account a ON a.id=c.account_id WHERE "
                        + scope.workerFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery credentials(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "MANAGER", "CAREGIVER");
        sql =
                "SELECT x.*,c.name caregiver_name FROM credential x JOIN caregiver c ON"
                        + " c.id=x.caregiver_id JOIN account a ON a.id=c.account_id WHERE "
                        + scope.workerFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery hours(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "MANAGER", "CAREGIVER");
        sql =
                "SELECT h.*,d.order_id,c.name caregiver_name FROM attendance h JOIN"
                        + " v_dispatch_context d ON d.id=h.dispatch_id JOIN caregiver c ON"
                        + " c.id=d.caregiver_id JOIN account a ON a.id=c.account_id WHERE "
                        + scope.workerFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery credentialDue(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "MANAGER");
        sql = "SELECT * FROM v_credential_due WHERE organization_id=?";
        parameters.add(user.organization());
        return new PageQuery(sql, parameters);
    }

    private PageQuery shiftCapacity(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "MANAGER");
        sql = "SELECT * FROM v_shift_capacity WHERE organization_id=?";
        parameters.add(user.organization());
        return new PageQuery(sql, parameters);
    }

    private PageQuery hoursReview(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "MANAGER");
        sql = "SELECT * FROM v_hours_review WHERE organization_id=?";
        parameters.add(user.organization());
        return new PageQuery(sql, parameters);
    }

    private PageQuery monthlyHours(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "MANAGER", "CAREGIVER");
        sql =
                "SELECT c.id,c.name,DATE_FORMAT(h.check_in,'%Y-%m') period,SUM(h.effective_minutes)"
                    + " effective_minutes FROM attendance h JOIN v_dispatch_context d ON"
                    + " d.id=h.dispatch_id JOIN caregiver c ON c.id=d.caregiver_id JOIN account a"
                    + " ON a.id=c.account_id WHERE h.status='VALID' AND "
                        + scope.workerFilter(user, parameters)
                        + " GROUP BY c.id,c.name,DATE_FORMAT(h.check_in,'%Y-%m')";
        return new PageQuery(sql, parameters);
    }

    private PageQuery quality(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "MANAGER", "REGULATOR");
        sql =
                "SELECT c.id,c.name,c.organization_id,(SELECT ROUND(AVG(q.rating),2) FROM complaint"
                    + " q WHERE EXISTS(SELECT 1 FROM v_dispatch_context d WHERE"
                    + " d.order_id=q.order_id AND d.caregiver_id=c.id AND d.status='FINISHED'))"
                    + " service_rating,(SELECT ROUND(AVG(i.score),2) FROM inspection i WHERE"
                    + " i.caregiver_id=c.id OR EXISTS(SELECT 1 FROM v_dispatch_context d WHERE"
                    + " d.order_id=i.order_id AND d.caregiver_id=c.id AND d.status='FINISHED'))"
                    + " inspection_score FROM caregiver c JOIN account a ON a.id=c.account_id WHERE"
                    + " "
                        + scope.workerFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery recruitments(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "MANAGER");
        sql =
                "SELECT id,candidate_name,status,caregiver_id,review_note,created_at,reviewed_at"
                        + " FROM recruitment WHERE organization_id=?";
        parameters.add(user.organization());
        return new PageQuery(sql, parameters);
    }

    private PageQuery trainings(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "MANAGER", "CAREGIVER");
        sql = "SELECT * FROM training WHERE organization_id=?";
        parameters.add(user.organization());
        return new PageQuery(sql, parameters);
    }

    private PageQuery trainingRecords(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "MANAGER", "CAREGIVER");
        sql =
                "SELECT r.*,t.title,c.name FROM training_record r JOIN training t ON"
                        + " t.id=r.training_id JOIN caregiver c ON c.id=r.caregiver_id WHERE ";
        if (user.role().equals("MANAGER")) {
            sql += "t.organization_id=?";
            parameters.add(user.organization());
        } else {
            sql += "c.account_id=?";
            parameters.add(user.id());
        }
        return new PageQuery(sql, parameters);
    }

    private PageQuery areas(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "MANAGER", "CAREGIVER", "ADMIN");
        sql =
                "SELECT a.id,a.caregiver_id,c.name,co.name community_name,a.community_id FROM"
                    + " caregiver_area a JOIN caregiver c ON c.id=a.caregiver_id JOIN community co"
                    + " ON co.id=a.community_id WHERE ";
        if (user.role().equals("CAREGIVER")) {
            sql += "c.account_id=?";
            parameters.add(user.id());
        } else {
            sql += scope.organizationFilter(user, parameters, "c.organization_id");
        }
        return new PageQuery(sql, parameters);
    }

    private Object caregiversDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        Auth.role(user, "ADMIN", "MANAGER", "CAREGIVER", "REGULATOR");
        scope.worker(user, recordId);
        out.put("护工档案", db.one("SELECT * FROM caregiver WHERE id=?", recordId));
        out.put(
                "服务区域",
                db.rows(
                        "SELECT a.id,c.name,c.id community_id FROM caregiver_area a JOIN community"
                                + " c ON c.id=a.community_id WHERE a.caregiver_id=?",
                        recordId));
        out.put(
                "项目资格",
                db.rows(
                        "SELECT x.*,s.name item_name FROM credential x JOIN service_item s ON"
                                + " s.id=x.item_id WHERE x.caregiver_id=?",
                        recordId));
        out.put(
                "培训记录",
                db.rows(
                        "SELECT r.*,t.title FROM training_record r JOIN training t ON"
                                + " t.id=r.training_id WHERE r.caregiver_id=?",
                        recordId));
        out.put(
                "累计有效工时",
                Map.of(
                        "effective_minutes",
                        db.count(
                                "SELECT COALESCE(SUM(a.effective_minutes),0) FROM attendance a JOIN"
                                        + " v_dispatch_context d ON d.id=a.dispatch_id WHERE"
                                        + " d.caregiver_id=? AND a.status='VALID'",
                                recordId)));
        return out;
    }
}
