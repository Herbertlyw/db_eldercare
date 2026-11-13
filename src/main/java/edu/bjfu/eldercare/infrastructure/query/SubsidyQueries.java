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
public class SubsidyQueries implements QueryModule {

    private final Store db;

    private final QueryScope scope;

    public SubsidyQueries(Store db, QueryScope scope) {
        this.db = db;
        this.scope = scope;
    }

    public Map<String, QueryHandler> queries() {
        return Map.ofEntries(
                Map.entry("claims", this::claims),
                Map.entry("eligibleClaims", this::eligibleClaims),
                Map.entry("claimProgress", this::claims),
                Map.entry("selfPay", this::selfPay),
                Map.entry("policies", this::policies));
    }

    public Map<String, DetailHandler> details() {
        return Map.ofEntries(
                Map.entry("claims", this::claimsDetails),
                Map.entry("claimProgress", this::claimsDetails));
    }

    private PageQuery claims(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "FAMILY", "STREET", "REGULATOR");
        sql =
                "SELECT c.*,e.name elder_name,p.reference_no,p.paid_at FROM claim c JOIN elder e ON"
                        + " e.id=c.elder_id LEFT JOIN payment p ON p.claim_id=c.id WHERE "
                        + scope.claimFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery eligibleClaims(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN");
        sql = "SELECT * FROM v_claim_eligible WHERE community_id=?";
        parameters.add(user.community());
        return new PageQuery(sql, parameters);
    }

    private PageQuery selfPay(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "FAMILY");
        sql =
                "SELECT o.id,e.name elder_name,o.amount,COALESCE((SELECT SUM(ci.amount) FROM"
                    + " claim_item ci JOIN claim cl ON cl.id=ci.claim_id WHERE ci.order_id=o.id AND"
                    + " cl.status='PAID'),0) paid_subsidy,o.amount-COALESCE((SELECT SUM(ci.amount)"
                    + " FROM claim_item ci JOIN claim cl ON cl.id=ci.claim_id WHERE"
                    + " ci.order_id=o.id AND cl.status='PAID'),0) self_pay FROM service_order o"
                    + " JOIN elder e ON e.id=o.elder_id WHERE o.status='COMPLETED' AND "
                        + scope.elderFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery policies(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql =
                "SELECT DISTINCT pol.* FROM policy pol JOIN policy_area pa ON pa.policy_id=pol.id"
                        + " WHERE ";
        if (user.role().equals("FAMILY")) {
            parameters.add(user.id());
            sql +=
                    "EXISTS(SELECT 1 FROM guardian g JOIN elder e ON e.id=g.elder_id WHERE"
                            + " g.account_id=? AND g.active=1 AND e.community_id=pa.community_id)";
        } else {
            sql += scope.communityFilter(user, "pa.community_id", parameters);
        }
        return new PageQuery(sql, parameters);
    }

    private Object claimsDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        Auth.role(user, "ADMIN", "FAMILY", "STREET", "REGULATOR");
        out.put("补贴申报", scope.claim(user, recordId));
        out.put(
                "订单与核算依据",
                db.rows(
                        "SELECT ci.*,o.starts_at,o.amount order_amount,s.name"
                            + " item_name,r.notes,r.confirmation,r.confirmed_at,a.check_in,a.check_out,a.review_reason"
                            + " FROM claim_item ci JOIN service_order o ON o.id=ci.order_id JOIN"
                            + " service_item s ON s.id=o.item_id JOIN v_dispatch_context d ON"
                            + " d.order_id=o.id AND d.status='FINISHED' JOIN service_record r ON"
                            + " r.dispatch_id=d.id JOIN attendance a ON a.dispatch_id=d.id WHERE"
                            + " ci.claim_id=?",
                        recordId));
        out.put(
                "审核记录",
                db.rows(
                        "SELECT cr.*,a.username reviewer_name FROM claim_review cr JOIN account a"
                                + " ON a.id=cr.reviewer_id WHERE cr.claim_id=? ORDER BY cr.id",
                        recordId));
        out.put(
                "归档材料",
                db.rows(
                        "SELECT id,name,content,content_sha256,created_at FROM claim_evidence WHERE"
                                + " claim_id=?",
                        recordId));
        out.put(
                "拨付凭据",
                db.rows(
                        "SELECT reference_no,amount,paid_at FROM payment WHERE claim_id=?",
                        recordId));
        return out;
    }
}
