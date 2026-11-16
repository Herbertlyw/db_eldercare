package edu.bjfu.eldercare.infrastructure.bootstrap;

import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orders are advanced in sequence; completed examples include confirmation and real hour checks.
 */
final class ScenarioServices {
    record Completed(long order, ScenarioCare.Person person, int itemIndex, LocalDateTime start) {}

    final List<Completed> completed = new ArrayList<>();
    private final Store db;
    private final ScenarioCare care;

    ScenarioServices(Store db, ScenarioCare care) {
        this.db = db;
        this.care = care;
    }

    void create() {
        for (int i = 0; i < care.people.size(); i++) {
            var person = care.people.get(i);
            for (int j = 0; j < 3; j++) {
                LocalDateTime start =
                        care.now.toLocalDate().minusDays(12 - j * 3).atTime(i % 2 == 0 ? 9 : 14, 0);
                long order = order(person, j, start, "COMPLETED", "VALID", i + j);
                completed.add(new Completed(order, person, j, start));
            }
        }
        String[] states = {"PENDING", "ASSIGNED", "ACCEPTED", "SERVING", "CONFIRM", "CANCELLED"};
        for (int i = 0; i < states.length; i++) {
            var person = care.people.get(i);
            LocalDateTime start =
                    i == 3
                            ? care.now.minusMinutes(10)
                            : i == 4
                                    ? care.now.toLocalDate().minusDays(1).atTime(10, 0)
                                    : care.now.toLocalDate().plusDays(2 + i).atTime(10, 0);
            order(person, 3 + i % 3, start, states[i], "VALID", i);
        }
        order(
                care.people.get(0),
                1,
                care.now.toLocalDate().minusDays(2).atTime(9, 0),
                "COMPLETED",
                "REVIEW",
                0);
        order(
                care.people.get(1),
                1,
                care.now.toLocalDate().minusDays(2).atTime(14, 0),
                "COMPLETED",
                "INVALID",
                1);
        historyDispatch("REJECTED", 8);
        historyDispatch("REASSIGNED", 9);
        order(
                care.people.get(0),
                4,
                care.now.toLocalDate().minusDays(1).atTime(9, 0),
                "CONFIRM",
                "VALID",
                1);
        order(
                care.people.get(1),
                5,
                care.now.toLocalDate().plusDays(7).atTime(15, 0),
                "CANCELLED",
                "VALID",
                0);
        for (int i = 0; i < 3; i++) {
            long worker = care.workers.get(i == 2 ? 4 : i);
            LocalDateTime start = care.now.toLocalDate().plusDays(2).atTime(8, 0);
            db.update(
                    "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity) VALUES(?,?,?,4)",
                    worker,
                    start,
                    start.plusHours(10));
        }
        feedback();
    }

    private long order(
            ScenarioCare.Person p,
            int itemIndex,
            LocalDateTime start,
            String state,
            String hours,
            int channelIndex) {
        long item = care.items.get(itemIndex);
        var product =
                db.one("SELECT price,unit,duration_minutes FROM service_item WHERE id=?", item);
        int minutes = ((Number) product.get("duration_minutes")).intValue();
        LocalDateTime end = start.plusMinutes(minutes);
        BigDecimal price = (BigDecimal) product.get("price");
        BigDecimal amount =
                "HOUR".equals(product.get("unit"))
                        ? price.multiply(BigDecimal.valueOf(minutes))
                                .divide(BigDecimal.valueOf(60), 2, java.math.RoundingMode.HALF_UP)
                        : price;
        String channel = new String[] {"PHONE", "FAMILY", "COMMUNITY"}[channelIndex % 3];
        var elder = db.one("SELECT address,position_x,position_y FROM elder WHERE id=?", p.id());
        long order =
                db.insert(
                        "INSERT INTO"
                            + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,created_at,position_x,position_y)"
                            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                        p.id(),
                        item,
                        p.community(),
                        start,
                        end,
                        elder.get("address"),
                        channel,
                        amount,
                        channel.equals("FAMILY") ? p.family() : p.admin(),
                        pastOrNow(start.minusDays(2), 120),
                        elder.get("position_x"),
                        elder.get("position_y"));
        if (state.equals("PENDING") || state.equals("CANCELLED")) {
            if (state.equals("CANCELLED")) {
                db.update("UPDATE service_order SET status='CANCELLED' WHERE id=?", order);
            }
            return order;
        }
        long shift =
                db.insert(
                        "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity,used_count)"
                                + " VALUES(?,?,?,1,1)",
                        p.worker(),
                        start.minusMinutes(15),
                        end.plusMinutes(15));
        db.call("assert_worker_order", order, shift);
        long dispatch =
                db.insert(
                        "INSERT INTO dispatch(order_id,shift_id,reason,assigned_at)"
                                + " VALUES(?,?,'按服务区域和专项资质安排',?)",
                        order,
                        shift,
                        pastOrNow(start.minusDays(1), 60));
        db.update("UPDATE service_order SET status='ASSIGNED' WHERE id=?", order);
        if (state.equals("ASSIGNED")) {
            return order;
        }
        db.update(
                "UPDATE dispatch SET status='ACCEPTED',accepted_at=? WHERE id=?",
                pastOrNow(start.minusHours(3), 30),
                dispatch);
        db.update("UPDATE service_order SET status='ACCEPTED' WHERE id=?", order);
        if (state.equals("ACCEPTED")) {
            return order;
        }
        db.update(
                "INSERT INTO"
                    + " attendance(dispatch_id,check_in,location_note,fence_ok,checkin_x,checkin_y)"
                    + " SELECT ?,?,'已到老人住所',1,position_x,position_y FROM elder WHERE id=?",
                dispatch,
                start,
                p.id());
        db.update("UPDATE service_order SET status='SERVING' WHERE id=?", order);
        if (state.equals("SERVING")) {
            return order;
        }
        boolean outside = !hours.equals("VALID");
        db.update(
                "UPDATE attendance a JOIN elder e ON e.id=? SET"
                    + " a.check_out=?,a.checkout_fence_ok=?,a.checkout_x=e.position_x+?,a.checkout_y=e.position_y"
                    + " WHERE dispatch_id=?",
                p.id(),
                end,
                !outside,
                outside ? 800 : 0,
                dispatch);
        db.call("verify_hours", dispatch);
        if (hours.equals("INVALID")) {
            db.update(
                    "UPDATE attendance SET"
                        + " status='INVALID',effective_minutes=0,reviewer_id=?,review_reason='核对到访记录，签退位置超出范围，本次不计有效工时'"
                        + " WHERE dispatch_id=?",
                    p.manager(),
                    dispatch);
        }
        db.update(
                "INSERT INTO service_record(dispatch_id,notes) VALUES(?,?)",
                dispatch,
                new String[] {
                            "已送达午餐，协助用餐并整理餐具。",
                            "完成客厅和厨房清洁，常用物品放回原位。",
                            "陪同完成门诊就诊和取药，交接药品与就诊单。",
                            "完成助浴和更衣，离开前清理浴室。",
                            "在小区内散步并陪同交流，送回住所。",
                            "按既定照护安排协助完成活动训练。"
                        }
                        [itemIndex]);
        db.update("UPDATE dispatch SET status='FINISHED' WHERE id=?", dispatch);
        db.update("UPDATE service_order SET status='CONFIRM' WHERE id=?", order);
        if (state.equals("CONFIRM")) {
            return order;
        }
        db.update(
                "UPDATE service_record SET"
                        + " confirmed_by=?,confirmed_at=?,confirmation='已核对服务内容，确认本次服务完成' WHERE"
                        + " dispatch_id=?",
                p.family(),
                end.plusHours(2),
                dispatch);
        db.update("UPDATE service_order SET status='COMPLETED' WHERE id=?", order);
        return order;
    }

    private void historyDispatch(String state, int day) {
        var p = care.people.get(0);
        LocalDateTime start = care.now.toLocalDate().plusDays(day).atTime(10, 0);
        long order = order(p, 0, start, "ASSIGNED", "VALID", 0);
        var dispatch = db.one("SELECT id,shift_id FROM dispatch WHERE order_id=?", order);
        db.update(
                "UPDATE dispatch SET status=?,reason=? WHERE id=?",
                state,
                state.equals("REJECTED") ? "护工临时有事，退回社区重新安排" : "值班调整，转交另一班次办理",
                dispatch.get("id"));
        db.update("UPDATE shift SET used_count=0 WHERE id=?", dispatch.get("shift_id"));
        db.update("UPDATE service_order SET status='PENDING' WHERE id=?", order);
    }

    private LocalDateTime pastOrNow(LocalDateTime time, int minutes) {
        return time.isAfter(care.now.minusMinutes(minutes)) ? care.now.minusMinutes(minutes) : time;
    }

    private void feedback() {
        String[] states = {"SUBMITTED", "ASSIGNED", "REPLIED", "CLOSED"};
        String[] issues = {
            "送餐晚于约定时间，希望提前告知。", "清洁结束后厨房台面仍有积水。", "陪诊交接时未详细说明取药位置。", "服务人员到访前没有电话联系。"
        };
        for (int i = 0; i < 4; i++) {
            var c = completed.get(i);
            long id =
                    db.insert(
                            "INSERT INTO"
                                + " complaint(order_id,account_id,rating,content,kind,created_at)"
                                + " VALUES(?,?,?,?, 'COMPLAINT',?)",
                            c.order(),
                            c.person().family(),
                            2 + i % 2,
                            issues[i],
                            c.start().plusHours(5));
            complaintStep(id, c.person().family(), "SUBMITTED", issues[i], c.start().plusHours(5));
            if (i >= 1) {
                db.update(
                        "UPDATE complaint SET"
                            + " status='ASSIGNED',organization_id=?,handler_id=?,updated_at=? WHERE"
                            + " id=?",
                        c.person().community(),
                        c.person().manager(),
                        c.start().plusHours(6),
                        id);
                complaintStep(
                        id,
                        c.person().admin(),
                        "ASSIGNED",
                        "已转交服务机构核实，安排负责人回访。",
                        c.start().plusHours(6));
            }
            if (i >= 2) {
                db.update(
                        "UPDATE complaint SET"
                            + " status='REPLIED',result='已与家属沟通，补充服务交接并调整到访提醒',updated_at=? WHERE"
                            + " id=?",
                        c.start().plusDays(1),
                        id);
                complaintStep(
                        id,
                        c.person().manager(),
                        "REPLIED",
                        "已核对当班记录，并向家属反馈处理安排。",
                        c.start().plusDays(1));
            }
            if (i == 3) {
                db.update(
                        "UPDATE complaint SET status='CLOSED',updated_at=? WHERE id=?",
                        c.start().plusDays(2),
                        id);
                complaintStep(
                        id,
                        c.person().admin(),
                        "CLOSED",
                        "回访确认问题已处理，家属认可处理结果。",
                        c.start().plusDays(2));
            }
        }
        for (int i = 4; i < completed.size(); i++) {
            var c = completed.get(i);
            int rating = 3 + i % 3;
            long id =
                    db.insert(
                            "INSERT INTO"
                                + " complaint(order_id,account_id,rating,content,status,kind,created_at)"
                                + " VALUES(?,?,?,?,'CLOSED','REVIEW',?)",
                            c.order(),
                            c.person().family(),
                            rating,
                            new String[] {"服务内容完成，希望到访时间更准时。", "沟通耐心，结束后交接得很清楚。", "照护细心，老人愿意继续预约。"}
                                    [rating - 3],
                            c.start().plusDays(1));
            complaintStep(id, c.person().family(), "CLOSED", "家属提交服务评价。", c.start().plusDays(1));
        }
    }

    private void complaintStep(
            long id, long actor, String state, String message, LocalDateTime time) {
        db.update(
                "INSERT INTO complaint_log(complaint_id,actor_id,status,message,created_at)"
                        + " VALUES(?,?,?,?,?)",
                id,
                actor,
                state,
                message,
                time);
    }
}
