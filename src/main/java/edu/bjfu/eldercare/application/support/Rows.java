package edu.bjfu.eldercare.application.support;

import java.util.Map;

/** JDBC 查询投影中数值编号的集中转换。 */
public final class Rows {

    private Rows() {}

    public static long id(Map<String, Object> row, String field) {
        return ((Number) row.get(field)).longValue();
    }
}
