package edu.bjfu.eldercare.infrastructure.bootstrap;

import edu.bjfu.eldercare.application.port.Crypto;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Optional, additive course scenarios. All modules share one transaction and retain DB guards. */
@Component
@ConditionalOnProperty(name = "app.demo-scenarios", havingValue = "true")
public class DemoScenarios implements CommandLineRunner {
    static final String MARKER = "seed:business-scenarios-v1";
    private final Store db;
    private final Crypto crypto;

    public DemoScenarios(Store db, Crypto crypto) {
        this.db = db;
        this.crypto = crypto;
    }

    @Override
    @Transactional
    public void run(String... args) {
        db.one("SELECT id FROM community WHERE id=1 AND area_code='DEMO-1' FOR UPDATE");
        if (db.count("SELECT COUNT(*) FROM audit_log WHERE actor=? AND operation='IMPORT'", MARKER)
                > 0) {
            return;
        }
        db.one("SELECT id FROM community WHERE id=2 AND area_code='DEMO-2'");
        for (int community = 1; community <= 2; community++) {
            long base = (community - 1) * 6L;
            for (int i = 1; i <= 6; i++) {
                String role =
                        new String[] {
                                    "ADMIN", "FAMILY", "CAREGIVER", "MANAGER", "STREET", "REGULATOR"
                                }
                                [i - 1];
                db.one(
                        "SELECT id FROM account WHERE id=? AND username=? AND role=? AND"
                                + " community_id=? AND active=1",
                        base + i,
                        role.toLowerCase() + community,
                        role,
                        community);
            }
        }
        if (db.count("SELECT COUNT(*) FROM elder WHERE id BETWEEN 21001 AND 21007") > 0) {
            throw new Rule("业务示例档案编号已占用；未完成的导入或编号冲突需要先核对，不能覆盖");
        }
        try {
            db.update("SET @actor=?", MARKER);
            var care = new ScenarioCare(db, crypto, LocalDateTime.now().withNano(0));
            care.create();
            var services = new ScenarioServices(db, care);
            services.create();
            new ScenarioMonitoring(db, care).create();
            new ScenarioGovernance(db, care, services).create();
            db.update(
                    "INSERT INTO audit_log(table_name,row_id,operation,actor,new_status)"
                            + " VALUES('demo_scenarios',1,'IMPORT',?,'COMPLETE')",
                    MARKER);
        } finally {
            db.update("SET @actor=NULL");
        }
    }
}
