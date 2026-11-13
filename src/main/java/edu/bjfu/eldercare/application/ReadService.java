package edu.bjfu.eldercare.application;

import edu.bjfu.eldercare.application.port.QueryExecutor;
import edu.bjfu.eldercare.application.port.query.DetailHandler;
import edu.bjfu.eldercare.application.port.query.PageQuery;
import edu.bjfu.eldercare.application.port.query.QueryHandler;
import edu.bjfu.eldercare.application.port.query.QueryModule;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 读入口：统一分发、校验分页和执行参数化查询。 */
@Service
public class ReadService {

    private final QueryExecutor executor;

    private final Map<String, QueryHandler> queries;

    private final Map<String, DetailHandler> details;

    public ReadService(QueryExecutor executor, List<QueryModule> modules) {
        this.executor = executor;
        Map<String, QueryHandler> lists = new LinkedHashMap<>();
        Map<String, DetailHandler> records = new LinkedHashMap<>();
        for (QueryModule module : modules) {
            Routes.add(lists, module.queries());
            Routes.add(records, module.details());
        }
        queries = Map.copyOf(lists);
        details = Map.copyOf(records);
    }

    public Set<String> supportedQueries() {
        return queries.keySet();
    }

    public Set<String> supportedDetails() {
        return details.keySet();
    }

    public boolean handles(String kind) {
        return queries.containsKey(kind);
    }

    public Object list(Auth.User user, String kind, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            throw new Rule("分页参数无效");
        }
        QueryHandler handler = queries.get(kind);
        if (handler == null) {
            throw new Rule("未知数据类别");
        }
        PageQuery query = handler.prepare(user);
        return executor.page(query, page, size);
    }

    public Object details(Auth.User user, String kind, long id) {
        DetailHandler handler = details.get(kind);
        if (handler == null) {
            throw new Rule("该类别不提供详情");
        }
        return handler.read(user, id);
    }

    public Object publicNotices() {
        return executor.publicNotices();
    }
}
