package edu.bjfu.eldercare.application.port.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** 参数化查询及可选的行转换；不把用户输入拼入 SQL。 */
public record PageQuery(
        String sql,
        List<Object> parameters,
        UnaryOperator<Map<String, Object>> rowMapper,
        String orderBy,
        String summaryColumns) {

    public PageQuery {
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    public PageQuery(String sql, List<Object> parameters) {
        this(sql, parameters, UnaryOperator.identity(), "1 DESC", "");
    }

    public PageQuery(
            String sql, List<Object> parameters, UnaryOperator<Map<String, Object>> rowMapper) {
        this(sql, parameters, rowMapper, "1 DESC", "");
    }

    public PageQuery withSummary(String columns) {
        return new PageQuery(sql, parameters, rowMapper, orderBy, columns);
    }

    /** 排序仅由查询模块定义，不接受请求中的 SQL 或字段名。 */
    public PageQuery orderedBy(String order) {
        return new PageQuery(sql, parameters, rowMapper, order, summaryColumns);
    }
}
