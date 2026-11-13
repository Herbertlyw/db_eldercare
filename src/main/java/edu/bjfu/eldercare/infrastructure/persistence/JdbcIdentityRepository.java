package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.application.port.IdentityRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIdentityRepository implements IdentityRepository {

    private final Store db;

    public JdbcIdentityRepository(Store db) {
        this.db = db;
    }

    public List<Map<String, Object>> findAccountForUpdate(String username) {
        var accounts = db.rows("SELECT * FROM account WHERE username=? FOR UPDATE", username);
        for (var account : accounts) {
            if (account.get("locked_until") instanceof java.sql.Timestamp timestamp) {
                account.put("locked_until", timestamp.toLocalDateTime());
            }
        }
        return accounts;
    }

    public List<Map<String, Object>> findActiveAccount(long id) {
        return db.rows(
                "SELECT id,username,role,community_id,organization_id FROM account WHERE id=? AND"
                        + " active=1",
                id);
    }

    public void recordFailure(Object id) {
        db.update(
                "UPDATE account SET failures=failures+1,locked_until=IF(failures>=5,NOW()+INTERVAL"
                        + " 15 MINUTE,NULL) WHERE id=?",
                id);
    }

    public void resetFailures(Object id) {
        db.update("UPDATE account SET failures=0,locked_until=NULL WHERE id=?", id);
    }
}
