package edu.bjfu.eldercare.application.port.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/** 参数化查询及可选的行转换；不把用户输入拼入 SQL。 */
public record PageQuery(
        String sql, List<Object> parameters, UnaryOperator<Map<String, Object>> rowMapper) {

    public PageQuery {
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    public PageQuery(String sql, List<Object> parameters) {
        this(sql, parameters, UnaryOperator.identity());
    }
}
