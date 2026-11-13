package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.application.port.QueryExecutor;
import edu.bjfu.eldercare.application.port.query.PageQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcQueryExecutor implements QueryExecutor {

    private final Store db;

    public JdbcQueryExecutor(Store db) {
        this.db = db;
    }

    public Object page(PageQuery query, int page, int size) {
        List<Object> parameters = new ArrayList<>(query.parameters());
        long total = db.count("SELECT COUNT(*) FROM (" + query.sql() + ") q", parameters.toArray());
        parameters.add(size);
        parameters.add(page * size);
        var rows =
                db
                        .rows(
                                query.sql() + " ORDER BY 1 DESC LIMIT ? OFFSET ?",
                                parameters.toArray())
                        .stream()
                        .map(query.rowMapper())
                        .toList();
        return Map.of("rows", rows, "total", total, "page", page, "size", size);
    }

    public Object publicNotices() {
        return db.rows(
                "SELECT title,body,published_at FROM public_notice WHERE withdrawn_at IS NULL ORDER"
                        + " BY published_at DESC LIMIT 100");
    }
}
