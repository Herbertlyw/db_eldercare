package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.application.port.QueryExecutor;
import edu.bjfu.eldercare.application.port.query.ListFilter;
import edu.bjfu.eldercare.application.port.query.PageQuery;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcQueryExecutor implements QueryExecutor {

    private final Store db;
    private final ListFiltering filtering;

    public JdbcQueryExecutor(Store db, ListFiltering filtering) {
        this.filtering = filtering;
        this.db = db;
    }

    public Object page(PageQuery query, int page, int size) {
        return page(query, page, size, ListFilter.NONE);
    }

    public Object page(PageQuery source, int page, int size, ListFilter filter) {
        var prepared = filtering.prepare(source, filter);
        var query = prepared.query();
        List<Object> parameters = new ArrayList<>(query.parameters());
        String columns =
                query.summaryColumns().isEmpty()
                        ? "COUNT(*) total"
                        : "COUNT(*) total," + query.summaryColumns();
        var aggregate =
                new LinkedHashMap<>(
                        db.one(
                                "SELECT " + columns + " FROM (" + query.sql() + ") q",
                                parameters.toArray()));
        Object total = aggregate.remove("total");
        parameters.add(size);
        parameters.add(page * size);
        var rows =
                db
                        .rows(
                                query.sql() + " ORDER BY " + query.orderBy() + " LIMIT ? OFFSET ?",
                                parameters.toArray())
                        .stream()
                        .map(query.rowMapper())
                        .toList();
        var result =
                new LinkedHashMap<String, Object>(
                        Map.of(
                                "rows",
                                rows,
                                "total",
                                total,
                                "page",
                                page,
                                "size",
                                size,
                                "dateField",
                                prepared.dateField(),
                                "states",
                                prepared.states()));
        if (!aggregate.isEmpty()) result.put("summary", aggregate);
        return result;
    }

    public Object publicNotices() {
        return db.rows(
                "SELECT title,body,published_at FROM public_notice WHERE withdrawn_at IS NULL ORDER"
                        + " BY published_at DESC LIMIT 100");
    }
}
