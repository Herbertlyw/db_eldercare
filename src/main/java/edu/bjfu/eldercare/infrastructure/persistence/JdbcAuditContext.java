package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.application.port.AuditContext;
import org.springframework.stereotype.Component;

@Component
public class JdbcAuditContext implements AuditContext {

    private final Store db;

    public JdbcAuditContext(Store db) {
        this.db = db;
    }

    public void setActor(String actor) {
        db.update("SET @actor=?", actor);
    }
}
