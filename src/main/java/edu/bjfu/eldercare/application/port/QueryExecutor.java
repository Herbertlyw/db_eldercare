package edu.bjfu.eldercare.application.port;

import edu.bjfu.eldercare.application.port.query.PageQuery;

public interface QueryExecutor {

    Object page(PageQuery query, int page, int size);

    Object publicNotices();
}
