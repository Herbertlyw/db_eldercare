package edu.bjfu.eldercare.application.port;

import java.util.List;
import java.util.Map;

public interface IdentityRepository {

    List<Map<String, Object>> findAccountForUpdate(String username);

    List<Map<String, Object>> findActiveAccount(long id);

    void replacePassword(long id, String hash, boolean mustChange);

    void recordPasswordAudit(long id, String operation, String actor, String reason);

    void recordFailure(Object id);

    void resetFailures(Object id);
}
