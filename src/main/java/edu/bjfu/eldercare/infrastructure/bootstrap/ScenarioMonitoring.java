package edu.bjfu.eldercare.infrastructure.bootstrap;

import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Synthetic readings only; these ranges are classroom fixtures, not clinical recommendations. */
final class ScenarioMonitoring {
    private final Store db;
    private final ScenarioCare care;

    ScenarioMonitoring(Store db, ScenarioCare care) {
        this.db = db;
        this.care = care;
    }

    void create() {
        String[] names = {"心率", "收缩压", "血氧"};
        int[] lows = {50, 90, 93}, highs = {100, 150, 100}, normal = {72, 124, 97};
        List<Long> metrics = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            metrics.add(
                    db.insert(
                            "INSERT INTO metric_rule(metric,low_value,high_value,response_seconds)"
                                    + " VALUES(?,?,?,?)",
                            names[i],
                            lows[i],
                            highs[i],
                            300 - i * 60));
        }
        for (int i = 0; i < care.people.size(); i++) {
            var p = care.people.get(i);
            long device =
                    db.insert(
                            "INSERT INTO"
                                + " device(serial_no,type_name,location_note,community_id,simulation_enabled,simulation_metric_id,simulation_seconds)"
                                + " VALUES(?,?,?,?,?,?,?)",
                            "COURSE-SCENE-DEVICE-" + (i + 1),
                            new String[] {"腕式监测设备", "电子血压计", "指夹式血氧仪"}[i % 3],
                            "老人住所客厅",
                            p.community(),
                            i == 0,
                            metrics.get(i % 3),
                            120);
            long binding =
                    db.insert(
                            "INSERT INTO device_binding(device_id,elder_id,bound_at) VALUES(?,?,?)",
                            device,
                            p.id(),
                            care.now.minusDays(20));
            for (int j = 0; j < 8; j++) {
                db.update(
                        "INSERT INTO"
                            + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                            + " VALUES(?,?,?,?,?,'SIMULATOR')",
                        binding,
                        metrics.get(i % 3),
                        "SCENE-READING-" + j,
                        care.now.minusHours(12 - j),
                        normal[i % 3] + (j % 3) - 1);
            }
            long vital =
                    db.insert(
                            "INSERT INTO"
                                + " vital(binding_id,metric_rule_id,sample_key,collected_at,metric_value,source)"
                                + " VALUES(?,?,'SCENE-ABNORMAL',?,?,'SIMULATOR')",
                            binding,
                            metrics.get(i % 3),
                            care.now.minusMinutes(3),
                            i % 3 == 2 ? 89 : i % 2 == 0 ? highs[i % 3] + 8 : lows[i % 3] - 8);
            long event = db.count("SELECT id FROM emergency WHERE vital_id=?", vital);
            LocalDateTime eventTime =
                    (LocalDateTime)
                            db.one("SELECT occurred_at FROM emergency WHERE id=?", event)
                                    .get("occurred_at");
            step(event, p.admin(), "REPORT", "设备采样超出设定阈值，等待人工核实。", eventTime);
            db.update(
                    "INSERT INTO emergency_log(event_id,level,created_at,message)"
                            + " VALUES(?,1,?,'设备监测异常，通知社区值班人员')",
                    event,
                    eventTime);
            String state =
                    new String[] {
                                "UNCONFIRMED",
                                "OPEN",
                                "RESPONDED",
                                "CLOSED",
                                "FALSE_ALARM",
                                "CLOSED"
                            }
                            [i];
            progress(event, p, state, eventTime, false);
            if (i == 1) {
                db.update("UPDATE device SET status='OFFLINE' WHERE id=?", device);
            }
            if (i == 5) {
                db.update(
                        "UPDATE device_binding SET unbound_at=? WHERE id=?",
                        care.now.minusMinutes(1),
                        binding);
                db.update("UPDATE device SET status='RETIRED' WHERE id=?", device);
            }
        }
        for (int i = 0; i < 6; i++) {
            var p = care.people.get(i);
            LocalDateTime time = care.now.minusDays(3).minusHours(i);
            int level = 1 + i % 3;
            long event =
                    db.insert(
                            "INSERT INTO"
                                + " emergency(elder_id,source,level,occurred_at,deadline,status)"
                                + " VALUES(?,?,?,?,?,'OPEN')",
                            p.id(),
                            i % 2 == 0 ? "FAMILY_CALL" : "MANUAL",
                            level,
                            time,
                            time.plusMinutes(5));
            db.update(
                    "INSERT INTO emergency_log(event_id,level,created_at,message)"
                            + " VALUES(?,?,?,'接到求助，社区值班人员已收到通知')",
                    event,
                    level,
                    time);
            step(event, i % 2 == 0 ? p.family() : p.admin(), "REPORT", "老人起身时感到不适，联系社区协助查看。", time);
            progress(event, p, i == 0 ? "OPEN" : i == 1 ? "RESPONDED" : "CLOSED", time, i == 4);
        }
        // Upcoming assessment reminders are also visible without enabling background jobs.
        db.call("remind_assessments");
    }

    private void progress(
            long event, ScenarioCare.Person p, String state, LocalDateTime time, boolean late) {
        if (state.equals("UNCONFIRMED")) {
            return;
        }
        if (state.equals("FALSE_ALARM")) {
            db.update(
                    "UPDATE emergency SET"
                            + " status='FALSE_ALARM',closed_at=?,close_reason='核实为设备佩戴松动，重新佩戴后复测正常'"
                            + " WHERE id=?",
                    time,
                    event);
            step(event, p.admin(), "DISMISS", "已与家属核实，记录为误报。", time);
            return;
        }
        db.update("UPDATE emergency SET status='OPEN' WHERE id=?", event);
        step(event, p.admin(), "CONFIRM", "联系家属后确认需要社区跟进。", time);
        if (state.equals("OPEN")) {
            return;
        }
        boolean historical = time.isBefore(care.now.minusHours(1));
        LocalDateTime response = historical ? time.plusMinutes(late ? 8 : 2) : time;
        db.update(
                "UPDATE emergency SET status='RESPONDED',responded_by=?,responded_at=? WHERE id=?",
                p.admin(),
                response,
                event);
        step(event, p.admin(), "RESPOND", "值班人员接手处置并联系家属。", response);
        if (state.equals("RESPONDED")) {
            return;
        }
        LocalDateTime arrived = historical ? response.plusMinutes(8) : response;
        LocalDateTime closed = historical ? arrived.plusMinutes(25) : arrived;
        db.update("UPDATE emergency SET arrived_at=? WHERE id=?", arrived, event);
        step(event, p.admin(), "ARRIVE", "已到住所，与家属共同查看老人情况。", arrived);
        db.update(
                "UPDATE emergency SET status='CLOSED',closed_at=?,result='已联系家属到场陪护，完成交接并安排后续回访'"
                        + " WHERE id=?",
                closed,
                event);
        step(event, p.admin(), "CLOSE", "处置结束，后续由家属陪护并按约定回访。", closed);
    }

    private void step(long event, long actor, String action, String message, LocalDateTime time) {
        db.update(
                "INSERT INTO event_step(event_id,actor_id,action,message,created_at)"
                        + " VALUES(?,?,?,?,?)",
                event,
                actor,
                action,
                message,
                time);
    }
}
