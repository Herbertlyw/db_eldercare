package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.application.port.query.ListFilter;
import edu.bjfu.eldercare.application.port.query.PageQuery;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Filters wrap the authorized query; identifiers come only from server-owned metadata. */
@Component
public class ListFiltering {
    private final Store db;

    public ListFiltering(Store db) {
        this.db = db;
    }

    public record Prepared(PageQuery query, String dateField, List<String> states) {}

    public Prepared prepare(PageQuery source, ListFilter filter) {
        List<String> columns = db.columns(source.sql(), source.parameters().toArray());
        String date =
                List.of(
                                "starts_at",
                                "occurred_at",
                                "collected_at",
                                "check_in",
                                "assessed_at",
                                "created_at",
                                "published_at",
                                "valid_until")
                        .stream()
                        .filter(columns::contains)
                        .findFirst()
                        .orElse("");
        var parameters = new ArrayList<Object>(source.parameters());
        var conditions = new ArrayList<String>();
        if (!filter.search().isEmpty()) {
            var searchable =
                    List.of(
                                    "id",
                                    "name",
                                    "elder_name",
                                    "caregiver_name",
                                    "candidate_name",
                                    "username",
                                    "title",
                                    "item_name",
                                    "address",
                                    "serial_no",
                                    "certificate_no",
                                    "period",
                                    "content",
                                    "message",
                                    "summary")
                            .stream()
                            .filter(columns::contains)
                            .toList();
            var matches = new ArrayList<String>();
            for (String column : searchable) {
                matches.add("LOCATE(LOWER(?),LOWER(CAST(q.`" + column + "` AS CHAR)))>0");
                parameters.add(filter.search());
            }
            conditions.add(matches.isEmpty() ? "1=0" : "(" + String.join(" OR ", matches) + ")");
        }
        if (!filter.status().isEmpty()) {
            if (!columns.contains("status")) throw new Rule("当前列表不支持状态筛选");
            conditions.add("q.status=?");
            parameters.add(filter.status());
        }
        if (filter.from() != null || filter.to() != null) {
            if (date.isEmpty()) throw new Rule("当前列表不支持日期筛选");
            if (filter.from() != null) {
                // 班次投影可声明区间结束点，跨午夜的班次也属于次日。
                conditions.add(
                        columns.contains("overlap_ends_at")
                                ? "q.overlap_ends_at>?"
                                : "q.`" + date + "`>=?");
                parameters.add(filter.from().atStartOfDay());
            }
            if (filter.to() != null) {
                conditions.add("q.`" + date + "`<?");
                parameters.add(filter.to().plusDays(1).atStartOfDay());
            }
        }
        if (!filter.field().isEmpty()) {
            if (!columns.contains(filter.field())) throw new Rule("当前列表不支持该关联查询");
            conditions.add("q.`" + filter.field() + "`=?");
            parameters.add(filter.value());
        }
        List<String> states =
                columns.contains("status")
                        ? db
                                .rows(
                                        "SELECT DISTINCT status FROM ("
                                                + source.sql()
                                                + ") scoped WHERE status IS NOT NULL ORDER BY"
                                                + " status",
                                        source.parameters().toArray())
                                .stream()
                                .map(r -> r.get("status").toString())
                                .toList()
                        : List.of();
        String sql =
                "SELECT * FROM ("
                        + source.sql()
                        + ") q"
                        + (conditions.isEmpty()
                                ? ""
                                : " WHERE " + String.join(" AND ", conditions));
        return new Prepared(
                new PageQuery(
                        sql,
                        parameters,
                        source.rowMapper(),
                        source.orderBy(),
                        source.summaryColumns()),
                date,
                states);
    }
}
