package edu.bjfu.eldercare.infrastructure.persistence;

import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.RecordAccess;
import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 写操作使用的记录锁和访问范围检查；普通查询继续使用 Scope。 */
@Component
public class JdbcRecordAccess implements RecordAccess {

    private final Store db;

    private final Scope scope;

    public JdbcRecordAccess(Store db, Scope scope) {
        this.db = db;
        this.scope = scope;
    }

    public void elder(Auth.User user, long elder) {
        db.one("SELECT id FROM elder WHERE id=? FOR UPDATE", elder);
        scope.elder(user, elder);
    }

    public Map<String, Object> activeOrder(Auth.User user, long orderId) {
        var order = db.one("SELECT * FROM service_order WHERE id=? FOR UPDATE", orderId);
        elder(user, id(order, "elder_id"));
        if (user.role().equals("CAREGIVER")
                && db.count(
                                "SELECT COUNT(*) FROM v_dispatch_context d JOIN caregiver c ON"
                                    + " c.id=d.caregiver_id WHERE d.order_id=? AND c.account_id=?"
                                    + " AND d.status IN('WAITING','ACCEPTED')",
                                orderId,
                                user.id())
                        == 0) {
            throw new Forbidden();
        }
        return order;
    }
}
