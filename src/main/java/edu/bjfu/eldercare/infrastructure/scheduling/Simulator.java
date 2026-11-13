package edu.bjfu.eldercare.infrastructure.scheduling;

import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Course simulator: an administrator explicitly enables each bound device. */
@Service
public class Simulator {

    private final Store db;

    public Simulator(Store db) {
        this.db = db;
    }

    @Transactional
    public int tick() {
        db.update("SET @actor='simulator'");
        var devices =
                db.rows(
                        "SELECT d.id,b.id binding_id,d.simulation_metric_id FROM device d JOIN"
                            + " device_binding b ON b.device_id=d.id AND b.unbound_at IS NULL JOIN"
                            + " elder e ON e.id=b.elder_id WHERE d.simulation_enabled=1 AND"
                            + " d.status='ACTIVE' AND e.status='ACTIVE' AND (d.last_simulated_at IS"
                            + " NULL OR"
                            + " TIMESTAMPDIFF(SECOND,d.last_simulated_at,NOW(6))>=d.simulation_seconds)"
                            + " ORDER BY d.id LIMIT 500 FOR UPDATE SKIP LOCKED");
        int written = 0;
        for (var device : devices) {
            written +=
                    db.update(
                            "INSERT INTO"
                                + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                                + " SELECT"
                                + " ?,id,?,NOW(6),ROUND(low_value+(high_value-low_value)*(0.4+RAND()*0.2),2),'SIMULATOR'"
                                + " FROM metric_rule WHERE id=?",
                            device.get("binding_id"),
                            UUID.randomUUID().toString(),
                            device.get("simulation_metric_id"));
            db.update("UPDATE device SET last_simulated_at=NOW(6) WHERE id=?", device.get("id"));
        }
        return written;
    }
}
