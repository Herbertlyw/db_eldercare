package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.domain.shared.Rule;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class Store {

    public final JdbcTemplate jdbc;

    public Store(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> rows(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    public Map<String, Object> one(String sql, Object... args) {
        var r = rows(sql, args);
        if (r.isEmpty()) {
            throw new Rule("记录不存在");
        }
        return r.get(0);
    }

    public long insert(String sql, Object... args) {
        var k = new GeneratedKeyHolder();
        jdbc.update(
                c -> {
                    var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) {
                        p.setObject(i + 1, args[i]);
                    }
                    return p;
                },
                k);
        return Objects.requireNonNull(k.getKey()).longValue();
    }

    public int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    public long count(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }

    public void call(String name, Object... args) {
        if (!Set.of(
                        "assert_order",
                        "assert_worker_order",
                        "assign_order",
                        "verify_hours",
                        "submit_claim",
                        "expire_plans",
                        "escalate_events",
                        "remind_assessments")
                .contains(name)) {
            throw new Rule("非法过程");
        }
        jdbc.update(
                "CALL "
                        + name
                        + "("
                        + String.join(",", Collections.nCopies(args.length, "?"))
                        + ")",
                args);
    }

    public static long id(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).longValue();
    }
}
