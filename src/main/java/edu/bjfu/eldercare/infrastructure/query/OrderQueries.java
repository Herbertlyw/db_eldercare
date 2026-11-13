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
public class OrderQueries implements QueryModule {

    private final Store db;

    private final QueryScope scope;

    public OrderQueries(Store db, QueryScope scope) {
        this.db = db;
        this.scope = scope;
    }

    public Map<String, QueryHandler> queries() {
        return Map.ofEntries(
                Map.entry("orders", this::orders),
                Map.entry("dispatchPool", this::dispatchPool),
                Map.entry("waitingConfirmation", this::waitingConfirmation),
                Map.entry("reassignments", this::reassignments),
                Map.entry("managerDispatchPool", this::managerDispatchPool));
    }

    public Map<String, DetailHandler> details() {
        return Map.ofEntries(Map.entry("orders", this::ordersDetails));
    }

    private PageQuery orders(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        sql =
                "SELECT o.*,e.name elder_name FROM service_order o JOIN elder e ON e.id=o.elder_id"
                        + " WHERE "
                        + scope.orderFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery dispatchPool(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN");
        sql = "SELECT * FROM v_dispatch_overdue WHERE community_id=?";
        parameters.add(user.community());
        return new PageQuery(sql, parameters);
    }

    private PageQuery waitingConfirmation(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "FAMILY");
        sql =
                "SELECT v.* FROM v_wait_confirmation v JOIN elder e ON e.id=v.elder_id WHERE "
                        + scope.elderFilter(user, parameters);
        return new PageQuery(sql, parameters);
    }

    private PageQuery reassignments(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN");
        sql = "SELECT * FROM v_reassign_history WHERE community_id=?";
        parameters.add(user.community());
        return new PageQuery(sql, parameters);
    }

    private PageQuery managerDispatchPool(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "MANAGER");
        sql =
                "SELECT o.id,o.elder_id,o.starts_at,d.assigned_at,c.name caregiver_name FROM"
                        + " v_dispatch_overdue v JOIN service_order o ON o.id=v.id JOIN"
                        + " v_dispatch_context d ON d.order_id=o.id JOIN caregiver c ON"
                        + " c.id=d.caregiver_id WHERE d.status='WAITING' AND c.organization_id=?";
        parameters.add(user.organization());
        return new PageQuery(sql, parameters);
    }

    private Object ordersDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("订单", scope.order(user, recordId));
        List<Object> parameters = new ArrayList<>(List.of(recordId));
        String restriction = "";
        if (user.role().equals("CAREGIVER")) {
            restriction = " AND c.account_id=?";
            parameters.add(user.id());
        }
        if (user.role().equals("MANAGER")) {
            restriction = " AND c.organization_id=?";
            parameters.add(user.organization());
        }
        out.put(
                "派单与服务过程",
                db.rows(
                        "SELECT d.id dispatch_id,c.name"
                            + " caregiver_name,d.status,d.reason,d.assigned_at,d.accepted_at,a.check_in,a.check_out,a.location_note,a.fence_ok,a.checkout_fence_ok,a.effective_minutes,a.status"
                            + " hours_status,a.review_reason,r.notes,r.confirmation,r.confirmed_at"
                            + " FROM v_dispatch_context d JOIN caregiver c ON c.id=d.caregiver_id"
                            + " LEFT JOIN attendance a ON a.dispatch_id=d.id LEFT JOIN"
                            + " service_record r ON r.dispatch_id=d.id WHERE d.order_id=?"
                                + restriction
                                + " ORDER BY d.id",
                        parameters.toArray()));
        return out;
    }
}
