package edu.bjfu.eldercare.application.port.query;

import edu.bjfu.eldercare.domain.shared.Rule;
import java.time.LocalDate;
import java.util.Set;

/** Optional list filters; scope is always established by the query module first. */
public record ListFilter(
        String search, String status, LocalDate from, LocalDate to, String field, Long value) {
    public static final ListFilter NONE = new ListFilter("", "", null, null, "", null);

    public ListFilter {
        search = search == null ? "" : search.trim();
        status = status == null ? "" : status.trim();
        field = field == null ? "" : field;
        if (search.length() > 100 || status.length() > 40) throw new Rule("查询条件过长");
        if (from != null && to != null && from.isAfter(to)) throw new Rule("开始日期不能晚于结束日期");
        if (to != null && to.getYear() >= 9999) throw new Rule("结束日期超出范围");
        if (!field.isEmpty()
                && !Set.of(
                                "id",
                                "elder_id",
                                "caregiver_id",
                                "order_id",
                                "training_id",
                                "batch_id",
                                "community_id")
                        .contains(field)) throw new Rule("不支持该关联查询");
        if (field.isEmpty() != (value == null) || value != null && value <= 0)
            throw new Rule("关联查询参数无效");
    }
}
