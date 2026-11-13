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
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class FeedbackQueries implements QueryModule {

    private final Store db;

    private final QueryScope scope;

    public FeedbackQueries(Store db, QueryScope scope) {
        this.db = db;
        this.scope = scope;
    }

    public Map<String, QueryHandler> queries() {
        return Map.ofEntries(Map.entry("complaints", this::complaints));
    }

    public Map<String, DetailHandler> details() {
        return Map.ofEntries(Map.entry("complaints", this::complaintsDetails));
    }

    private PageQuery complaints(Auth.User user) {
        List<Object> parameters = new ArrayList<>();
        String sql;
        Auth.role(user, "ADMIN", "FAMILY", "MANAGER", "REGULATOR");
        sql =
                "SELECT c.*,e.name elder_name FROM complaint c JOIN service_order o ON"
                        + " o.id=c.order_id JOIN elder e ON e.id=o.elder_id WHERE ";
        if (user.role().equals("MANAGER")) {
            sql += "c.organization_id=?";
            parameters.add(user.organization());
        } else if (user.role().equals("FAMILY")) {
            sql += scope.elderFilter(user, parameters);
        } else {
            sql += scope.communityFilter(user, "o.community_id", parameters);
        }
        return new PageQuery(sql, parameters);
    }

    private Object complaintsDetails(Auth.User user, long recordId) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        Auth.role(user, "ADMIN", "FAMILY", "MANAGER", "REGULATOR");
        var c = db.one("SELECT * FROM complaint WHERE id=?", recordId);
        scope.order(user, id(c, "order_id"));
        if (user.role().equals("MANAGER")
                && !Objects.equals(c.get("organization_id"), user.organization())) {
            throw new Forbidden();
        }
        out.put("评价与投诉", c);
        out.put(
                "处理过程",
                db.rows(
                        "SELECT l.*,a.username actor_name FROM complaint_log l JOIN account a ON"
                                + " a.id=l.actor_id WHERE l.complaint_id=? ORDER BY l.id",
                        recordId));
        return out;
    }
}
