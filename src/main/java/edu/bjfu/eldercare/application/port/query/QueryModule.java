package edu.bjfu.eldercare.application.port.query;

import java.util.Map;

/** 查询模块负责业务 SQL 与授权；分页由 ReadService 统一处理。 */
public interface QueryModule {

    Map<String, QueryHandler> queries();

    default Map<String, DetailHandler> details() {
        return Map.of();
    }
}
