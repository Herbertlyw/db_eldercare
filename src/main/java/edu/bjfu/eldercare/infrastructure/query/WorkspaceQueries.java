package edu.bjfu.eldercare.infrastructure.query;

import edu.bjfu.eldercare.application.port.query.PageQuery;
import edu.bjfu.eldercare.application.port.query.QueryHandler;
import edu.bjfu.eldercare.application.port.query.QueryModule;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.infrastructure.security.QueryScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 工作台的授权读投影。日程排序、完整集合的汇总均在数据库执行。 */
@Component
public class WorkspaceQueries implements QueryModule {
    private final QueryScope scope;

    public WorkspaceQueries(QueryScope scope) {
        this.scope = scope;
    }

    public Map<String, QueryHandler> queries() {
        return Map.ofEntries(
                Map.entry("workspaceProfile", this::profile),
                Map.entry("workspaceFamily", this::family),
                Map.entry("workspaceSchedule", u -> orders(u, "1=1")),
                Map.entry(
                        "workspaceUpcoming",
                        u ->
                                orders(
                                        u,
                                        "o.starts_at>=NOW() AND o.status IN"
                                                + " ('PENDING','ASSIGNED','ACCEPTED')")),
                Map.entry(
                        "workspaceCarryover",
                        u ->
                                orders(
                                        u,
                                        "o.starts_at<CURRENT_DATE AND o.status IN"
                                                + " ('ACCEPTED','SERVING')")),
                Map.entry("workspaceShifts", this::shifts),
                Map.entry("workspaceEvents", this::events),
                Map.entry("workspaceHours", this::hours));
    }

    private PageQuery profile(Auth.User u) {
        return new PageQuery(
                """
                SELECT a.id,COALESCE(w.name,a.username) display_name,c.name community_name,
                org.name organization_name,CURRENT_DATE today,NOW() server_time,
                DATE_FORMAT(CURRENT_DATE,'%Y-%m') period
                FROM account a JOIN community c ON c.id=a.community_id
                LEFT JOIN organization org ON org.id=a.organization_id
                LEFT JOIN caregiver w ON w.account_id=a.id WHERE a.id=?
                """,
                List.of(u.id()));
    }

    private PageQuery family(Auth.User u) {
        Auth.role(u, "FAMILY");
        var parameters = new ArrayList<Object>();
        String sql =
                """
SELECT e.id,e.id elder_id,e.name,e.birth_date,e.status,e.address,
co.name community_name,p.id plan_id,p.status plan_status,p.ends_at,
b.name bundle_name,c.name caregiver_name,a.valid_until,a.ability_level,
(SELECT COALESCE(SUM(o.amount),0) FROM service_order o
  WHERE o.elder_id=e.id AND o.status='COMPLETED') service_amount,
(SELECT COALESCE(SUM(ci.amount),0) FROM claim_item ci
  JOIN claim cl ON cl.id=ci.claim_id JOIN service_order o ON o.id=ci.order_id
  WHERE o.elder_id=e.id AND o.status='COMPLETED' AND cl.status='PAID') paid_subsidy
FROM elder e JOIN community co ON co.id=e.community_id
LEFT JOIN v_plan_context p ON p.id=(SELECT cp.id FROM v_plan_context cp
  WHERE cp.elder_id=e.id AND cp.starts_at<=NOW()
  ORDER BY (cp.status='ACTIVE' AND cp.ends_at>NOW()) DESC,cp.starts_at DESC,cp.id DESC LIMIT 1)
LEFT JOIN care_bundle b ON b.id=p.bundle_id
LEFT JOIN caregiver c ON c.id=p.caregiver_id
LEFT JOIN assessment a ON a.id=(SELECT aa.id FROM assessment aa
  WHERE aa.elder_id=e.id AND aa.assessed_at<=NOW()
  ORDER BY aa.assessed_at DESC,aa.id DESC LIMIT 1)
WHERE
"""
                        + scope.elderFilter(u, parameters);
        return new PageQuery(sql, parameters).orderedBy("id ASC");
    }

    private PageQuery orders(Auth.User u, String condition) {
        Auth.role(u, "ADMIN", "FAMILY", "CAREGIVER");
        var parameters = new ArrayList<Object>();
        String restriction = scope.orderFilter(u, parameters);
        // 待接和日程只列当前归属或本人已完成的任务，不把已改派任务算给原护工。
        if (u.role().equals("CAREGIVER")) {
            restriction +=
                    " AND EXISTS(SELECT 1 FROM v_dispatch_context own JOIN caregiver w"
                            + " ON w.id=own.caregiver_id WHERE own.order_id=o.id AND w.account_id=?"
                            + " AND own.status IN ('WAITING','ACCEPTED','FINISHED'))";
            parameters.add(u.id());
        }
        return new PageQuery(
                        """
SELECT o.*,IF(o.ends_at>NOW(),1,2) dispatch_window,e.name elder_name,s.name item_name,
c.name caregiver_name,c.account_id current_worker_account
FROM service_order o JOIN elder e ON e.id=o.elder_id
JOIN service_item s ON s.id=o.item_id
LEFT JOIN v_dispatch_context d ON d.id=(SELECT dx.id FROM v_dispatch_context dx
  WHERE dx.order_id=o.id AND dx.status IN ('WAITING','ACCEPTED','FINISHED')
  ORDER BY dx.id DESC LIMIT 1)
LEFT JOIN caregiver c ON c.id=d.caregiver_id WHERE
"""
                                + restriction
                                + " AND "
                                + condition,
                        parameters)
                .orderedBy("starts_at ASC,id ASC");
    }

    private PageQuery shifts(Auth.User u) {
        Auth.role(u, "CAREGIVER", "MANAGER");
        var parameters = new ArrayList<Object>();
        return new PageQuery(
                        "SELECT s.*,s.ends_at overlap_ends_at,c.name caregiver_name,c.status"
                            + " worker_status FROM shift s JOIN caregiver c ON c.id=s.caregiver_id"
                            + " JOIN account a ON a.id=c.account_id WHERE "
                                + scope.workerFilter(u, parameters),
                        parameters,
                        row -> {
                            row.remove("overlap_ends_at");
                            return row;
                        })
                .orderedBy("starts_at ASC,id ASC");
    }

    private PageQuery events(Auth.User u) {
        Auth.role(u, "FAMILY", "CAREGIVER", "ADMIN");
        var parameters = new ArrayList<Object>();
        return new PageQuery(
                        "SELECT x.*,e.name elder_name FROM emergency x JOIN elder e ON"
                            + " e.id=x.elder_id WHERE x.status NOT IN ('CLOSED','FALSE_ALARM') AND "
                                + scope.elderFilter(u, parameters),
                        parameters)
                .orderedBy("deadline ASC,id ASC");
    }

    private PageQuery hours(Auth.User u) {
        Auth.role(u, "CAREGIVER", "MANAGER");
        var parameters = new ArrayList<Object>();
        return new PageQuery(
                "SELECT 1 id,DATE_FORMAT(CURRENT_DATE,'%Y-%m')"
                    + " period,COALESCE(SUM(h.effective_minutes),0) effective_minutes FROM"
                    + " attendance h JOIN v_dispatch_context d ON d.id=h.dispatch_id JOIN caregiver"
                    + " c ON c.id=d.caregiver_id JOIN account a ON a.id=c.account_id WHERE"
                    + " h.status='VALID' AND h.check_in>=DATE_FORMAT(CURRENT_DATE,'%Y-%m-01') AND"
                    + " h.check_in<DATE_FORMAT(CURRENT_DATE+INTERVAL 1 MONTH,'%Y-%m-01') AND "
                        + scope.workerFilter(u, parameters),
                parameters);
    }
}
