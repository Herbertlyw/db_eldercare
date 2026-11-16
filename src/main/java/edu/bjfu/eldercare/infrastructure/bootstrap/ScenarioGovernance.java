package edu.bjfu.eldercare.infrastructure.bootstrap;

import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Subsidy and quality scenarios preserve their evidence, decisions and settlement amounts. */
final class ScenarioGovernance {
    private final Store db;
    private final ScenarioCare care;
    private final ScenarioServices services;

    ScenarioGovernance(Store db, ScenarioCare care, ScenarioServices services) {
        this.db = db;
        this.care = care;
        this.services = services;
    }

    void create() {
        claims();
        hourlyClaims();
        inspections();
        reports();
    }

    private void claims() {
        String[] states = {"DRAFT", "SUBMITTED", "STREET_APPROVED", "APPROVED", "REJECTED", "PAID"};
        for (int i = 0; i < states.length; i++) {
            var c = services.completed.get(i * 3);
            var p = c.person();
            String cycle = new String[] {"MONTH", "QUARTER", "YEAR"}[i % 3];
            String tag = new String[] {"ALONE", "DISABLED", "LOW_INCOME"}[i % 3];
            long policy =
                    db.insert(
                            "INSERT INTO"
                                + " policy(name,community_id,item_id,ability_level,rate,starts_at,ends_at,settlement_cycle,minimum_age,required_tag)"
                                + " VALUES(?,?,?,?,?,?,?,?,65,?)",
                            "社区助餐补助·" + new String[] {"月度", "季度", "年度"}[i % 3] + "·" + (i + 1),
                            p.community(),
                            care.items.get(0),
                            p.level(),
                            18 + i,
                            care.now.minusYears(1),
                            care.now.plusYears(1),
                            cycle,
                            tag);
            db.update(
                    "INSERT INTO policy_area(policy_id,community_id) VALUES(?,1),(?,2)",
                    policy,
                    policy);
            LocalDate start = c.start().toLocalDate().withDayOfMonth(1);
            if (cycle.equals("QUARTER")) {
                start = start.withMonth((start.getMonthValue() - 1) / 3 * 3 + 1);
            } else if (cycle.equals("YEAR")) {
                start = start.withDayOfYear(1);
            }
            LocalDate end =
                    cycle.equals("MONTH")
                            ? start.plusMonths(1)
                            : cycle.equals("QUARTER") ? start.plusMonths(3) : start.plusYears(1);
            String period =
                    cycle.equals("MONTH")
                            ? start.toString().substring(0, 7)
                            : cycle.equals("QUARTER")
                                    ? start.getYear() + "-Q" + ((start.getMonthValue() - 1) / 3 + 1)
                                    : String.valueOf(start.getYear());
            long claim;
            if (i == 0) {
                claim =
                        db.insert(
                                "INSERT INTO"
                                    + " claim(elder_id,item_id,period,community_id,period_start,period_end)"
                                    + " VALUES(?,?,?,?,?,?)",
                                p.id(),
                                care.items.get(0),
                                period,
                                p.community(),
                                start,
                                end);
            } else {
                db.call("submit_claim", p.id(), policy, period, "[" + c.order() + "]");
                claim =
                        db.count(
                                "SELECT id FROM claim WHERE elder_id=? AND item_id=? AND period=?",
                                p.id(),
                                care.items.get(0),
                                period);
            }
            db.update(
                    "INSERT INTO claim_evidence(claim_id,submitted_by,name,content,content_sha256)"
                        + " VALUES(?,?,'服务记录与家属确认','对应订单、有效工时及家属确认记录',SHA2('对应订单、有效工时及家属确认记录',256))",
                    claim,
                    p.admin());
            if (i == 4) {
                review(claim, p.street(), "STREET", "REJECT", "补充对象资格材料后重新提交。");
                db.update("UPDATE claim SET status='REJECTED' WHERE id=?", claim);
            } else if (i >= 2) {
                review(claim, p.street(), "STREET", "APPROVE", "服务记录及申报对象核验通过。");
                db.update("UPDATE claim SET status='STREET_APPROVED' WHERE id=?", claim);
                if (i >= 3) {
                    review(claim, p.regulator(), "REGULATOR", "APPROVE", "补助标准、服务明细及金额核对通过。");
                    db.update("UPDATE claim SET status='APPROVED' WHERE id=?", claim);
                }
                if (i == 5) {
                    db.update(
                            "INSERT INTO payment(claim_id,reference_no,amount)"
                                    + " VALUES(?,'COURSE-SCENARIO-PAY-1',?)",
                            claim,
                            db.one("SELECT amount FROM claim WHERE id=?", claim).get("amount"));
                    db.update("UPDATE claim SET status='PAID' WHERE id=?", claim);
                }
            }
        }
    }

    private void review(long claim, long actor, String stage, String decision, String reason) {
        db.update(
                "INSERT INTO claim_review(claim_id,reviewer_id,stage,decision,reason)"
                        + " VALUES(?,?,?,?,?)",
                claim,
                actor,
                stage,
                decision,
                reason);
    }

    private void hourlyClaims() {
        for (int i = 0; i < 2; i++) {
            var c = services.completed.get(i * 3 + 1);
            var p = c.person();
            long policy =
                    db.insert(
                            "INSERT INTO"
                                + " policy(name,community_id,item_id,ability_level,rate,starts_at,ends_at,settlement_cycle,minimum_age)"
                                + " VALUES(?,1,?,?,20,?,?,'MONTH',65)",
                            "居家清洁工时补助·" + (i + 1),
                            care.items.get(1),
                            p.level(),
                            care.now.minusYears(1),
                            care.now.plusYears(1));
            db.update("INSERT INTO policy_area(policy_id,community_id) VALUES(?,1)", policy);
            String period = c.start().toString().substring(0, 7);
            db.call("submit_claim", p.id(), policy, period, "[" + c.order() + "]");
            long claim =
                    db.count(
                            "SELECT id FROM claim WHERE elder_id=? AND item_id=? AND period=?",
                            p.id(),
                            care.items.get(1),
                            period);
            db.update(
                    "INSERT INTO claim_evidence(claim_id,submitted_by,name,content,content_sha256)"
                        + " VALUES(?,?,'服务工时核算单','按有效服务分钟折算小时，核对家属确认记录',SHA2('按有效服务分钟折算小时，核对家属确认记录',256))",
                    claim,
                    p.admin());
            review(claim, p.street(), "STREET", "APPROVE", "已核对服务工时和家属确认。");
            db.update("UPDATE claim SET status='STREET_APPROVED' WHERE id=?", claim);
            review(
                    claim,
                    p.regulator(),
                    "REGULATOR",
                    i == 0 ? "REJECT" : "APPROVE",
                    i == 0 ? "请补充对象资格复核材料后重新提交。" : "按小时核算金额无误，予以通过。");
            db.update(
                    "UPDATE claim SET status=? WHERE id=?",
                    i == 0 ? "REJECTED" : "APPROVED",
                    claim);
            if (i == 1) {
                db.update(
                        "INSERT INTO payment(claim_id,reference_no,amount)"
                                + " VALUES(?,'COURSE-SCENARIO-PAY-HOUR',?)",
                        claim,
                        db.one("SELECT amount FROM claim WHERE id=?", claim).get("amount"));
                db.update("UPDATE claim SET status='PAID' WHERE id=?", claim);
            }
        }
    }

    private void inspections() {
        String[] states = {"PENDING", "OPEN", "REVIEW", "CLOSED", "OPEN"};
        for (int i = 0; i < states.length; i++) {
            var c = services.completed.get(i == 4 ? 12 : 6 + i);
            var p = c.person();
            boolean orderTarget = i % 2 == 0;
            LocalDateTime time = care.now.minusDays(2);
            long batch =
                    db.insert(
                            "INSERT INTO"
                                + " inspection_batch(community_id,inspector_id,target_type,starts_at,ends_at,ratio,population,sample_count,sample_seed,created_at)"
                                + " VALUES(?,?,?,?,?,1,1,1,?,?)",
                            p.community(),
                            p.regulator(),
                            orderTarget ? "ORDER" : "CAREGIVER",
                            c.start(),
                            c.start().plusHours(2),
                            UUID.randomUUID().toString(),
                            time);
            long inspection =
                    db.insert(
                            "INSERT INTO"
                                + " inspection(order_id,caregiver_id,inspector_id,method,score,issue,rectification,status,batch_id,created_at)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                            orderTarget ? c.order() : null,
                            orderTarget ? null : p.worker(),
                            p.regulator(),
                            i % 2 == 0 ? "VISIT" : "PHONE",
                            null,
                            "",
                            "",
                            "PENDING",
                            batch,
                            time);
            if (i == 0) {
                continue;
            }
            db.update(
                    "UPDATE inspection SET"
                        + " status='OPEN',score=?,issue='服务交接记录缺少家属回访情况',rectification='补充回访记录并复核交接清单'"
                        + " WHERE id=?",
                    68 + i * 5,
                    inspection);
            if (i >= 2) {
                long submission =
                        db.insert(
                                "INSERT INTO"
                                    + " rectification_submission(inspection_id,submitted_by,measures,evidence,created_at)"
                                    + " VALUES(?,?,'逐项核对交接清单，补做家属回访','回访时间、处理内容和家属反馈记录',?)",
                                inspection,
                                p.manager(),
                                time.plusHours(4));
                db.update("UPDATE inspection SET status='REVIEW' WHERE id=?", inspection);
                if (i >= 3) {
                    boolean accept = i == 3;
                    db.update(
                            "UPDATE rectification_submission SET"
                                    + " decision=?,reviewer_id=?,review_note=?,reviewed_at=? WHERE"
                                    + " id=?",
                            accept ? "ACCEPT" : "RETURN",
                            p.regulator(),
                            accept ? "材料完整，回访内容与记录一致。" : "回访内容仍不完整，请补充处理结果。",
                            time.plusDays(1),
                            submission);
                    db.update(
                            "UPDATE inspection SET status=?,result=? WHERE id=?",
                            accept ? "CLOSED" : "OPEN",
                            accept ? "整改复核通过，完成归档。" : null,
                            inspection);
                    if (accept) {
                        db.update(
                                "INSERT INTO"
                                    + " public_notice(inspection_id,publisher_id,title,body,published_at)"
                                    + " VALUES(?,?,'社区上门服务抽查结果','本次抽查发现交接记录不完整，机构已补充回访并通过复核。公示不包含服务对象个人资料。',?)",
                                inspection,
                                p.regulator(),
                                time.plusDays(1).plusHours(1));
                        db.update(
                                "INSERT INTO"
                                    + " public_notice(inspection_id,publisher_id,title,body,published_at,withdrawn_at,withdraw_reason)"
                                    + " VALUES(?,?,'上门服务回访情况','回访材料核对完毕。',?,?,"
                                    + " '公示标题信息不完整，重新发布完整结果')",
                                inspection,
                                p.regulator(),
                                time.plusHours(12),
                                time.plusDays(1));
                    }
                }
            }
        }
    }

    private void reports() {
        for (int i = 0; i < 3; i++) {
            LocalDate month = care.now.toLocalDate().withDayOfMonth(1).minusMonths(i + 1);
            long report =
                    db.insert(
                            "INSERT INTO"
                                + " organization_report(organization_id,period,summary,evidence,submitted_by,created_at)"
                                + " VALUES(1,?,'本月开展上门助餐、清洁和陪诊服务；对回访发现的问题安排专人跟进。','订单明细、有效工时和服务回访记录',4,?)",
                            month.toString().substring(0, 7),
                            month.plusMonths(1).atStartOfDay());
            if (i > 0) {
                db.update(
                        "UPDATE organization_report SET"
                                + " status=?,reviewer_id=6,review_note=?,reviewed_at=? WHERE id=?",
                        i == 1 ? "ACCEPTED" : "RETURNED",
                        i == 1 ? "已核对订单、工时和回访材料，予以采纳。" : "请补充投诉处理及整改复核材料。",
                        month.plusMonths(1).plusDays(2).atStartOfDay(),
                        report);
            }
        }
    }
}
