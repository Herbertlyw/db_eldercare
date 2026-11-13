package edu.bjfu.eldercare.application.service;

import static edu.bjfu.eldercare.application.support.Inputs.choice;
import static edu.bjfu.eldercare.application.support.Inputs.decimal;
import static edu.bjfu.eldercare.application.support.Inputs.flag;
import static edu.bjfu.eldercare.application.support.Inputs.number;
import static edu.bjfu.eldercare.application.support.Inputs.text;
import static edu.bjfu.eldercare.application.support.Inputs.time;
import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.ActionHandler;
import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.domain.repository.GovernanceRepository;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 质量抽查、整改、机构考核与公示。 */
@Component
public class GovernanceService implements ActionModule {

    private final GovernanceRepository repository;

    private final Scope scope;

    public GovernanceService(GovernanceRepository repository, Scope scope) {
        this.repository = repository;
        this.scope = scope;
    }

    public Map<String, ActionHandler> actions() {
        return Map.ofEntries(
                Map.entry("inspection.create", this::createInspection),
                Map.entry("inspection.order", this::orderInspection),
                Map.entry("inspection.close", this::closeInspection),
                Map.entry("inspection.batch", this::batchInspection),
                Map.entry("inspection.record", this::recordInspection),
                Map.entry("rectification.submit", this::submitRectification),
                Map.entry("organization.submit", this::submitOrganization),
                Map.entry("organization.review", this::reviewOrganization),
                Map.entry("notice.publish", this::publishNotice),
                Map.entry("notice.withdraw", this::withdrawNotice));
    }

    private Object createInspection(Auth.User user, Map<String, Object> input) {
        return inspectTarget(user, input, false);
    }

    private Object orderInspection(Auth.User user, Map<String, Object> input) {
        return inspectTarget(user, input, true);
    }

    private Object closeInspection(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "REGULATOR");
        long recordId = number(input, "inspectionId");
        Map<String, Object> record = scope.inspection(user, recordId);
        repository.lockInspection(recordId);
        if (!record.get("status").equals("REVIEW")) {
            throw new Rule("请先由机构提交整改材料");
        }
        boolean approve = flag(input, "approve");
        String result = text(input, "result");
        repository.reviewRectification(approve ? "ACCEPT" : "RETURN", user.id(), result, recordId);
        return repository.updateInspectionResult(approve ? "CLOSED" : "OPEN", result, recordId);
    }

    private Object batchInspection(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "REGULATOR");
        long community = number(input, "communityId");
        scope.community(user, community);
        String type = choice(input, "target", "ORDER", "CAREGIVER");
        var start = time(input, "startsAt");
        var end = time(input, "endsAt");
        BigDecimal ratio = decimal(input, "ratio");
        if (!end.isAfter(start) || ratio.signum() <= 0 || ratio.compareTo(BigDecimal.ONE) > 0) {
            throw new Rule("抽查时间或比例无效");
        }
        String seed = UUID.randomUUID().toString();
        var population =
                type.equals("ORDER")
                        ? repository.findOrderPopulation(community, start, end, seed)
                        : repository.findCaregiverPopulation(community, start, end, seed);
        if (population.isEmpty()) {
            throw new Rule("抽查范围没有符合条件的对象");
        }
        int count =
                ratio.multiply(BigDecimal.valueOf(population.size()))
                        .setScale(0, RoundingMode.CEILING)
                        .intValue();
        long batch =
                repository.insertInspectionBatch(
                        community,
                        user.id(),
                        type,
                        start,
                        end,
                        ratio,
                        population.size(),
                        count,
                        seed);
        for (var target : population.subList(0, count)) {
            repository.insertSampledInspection(type, (Number) target.get("id"), user.id(), batch);
        }
        return batch;
    }

    private Object recordInspection(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "REGULATOR");
        long recordId = number(input, "inspectionId");
        Map<String, Object> record = scope.inspection(user, recordId);
        repository.lockInspection(recordId);
        if (!record.get("status").equals("PENDING")) {
            throw new Rule("抽查结果已经登记");
        }
        long score = number(input, "score");
        boolean rectify = flag(input, "requiresRectification") || score < 60;
        return repository.recordInspection(
                choice(input, "method", "PHONE", "VISIT"),
                score,
                text(input, "issue"),
                rectify ? text(input, "rectification") : "无需整改",
                rectify ? "OPEN" : "CLOSED",
                rectify ? null : "抽查通过",
                recordId);
    }

    private Object submitRectification(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long recordId = number(input, "inspectionId");
        Map<String, Object> record = scope.inspection(user, recordId);
        repository.lockInspection(recordId);
        if (!record.get("status").equals("OPEN")) {
            throw new Rule("当前没有待提交整改");
        }
        long submission =
                repository.insertRectification(
                        recordId, user.id(), text(input, "measures"), text(input, "evidence"));
        repository.markInspectionForReview(recordId);
        return submission;
    }

    private Object submitOrganization(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        String period = text(input, "period");
        if (!period.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new Rule("月份格式应为 YYYY-MM");
        }
        return repository.insertOrganizationReport(
                user.organization(),
                period,
                text(input, "summary"),
                text(input, "evidence"),
                user.id());
    }

    private Object reviewOrganization(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "REGULATOR");
        long recordId = number(input, "reportId");
        Map<String, Object> record = repository.findOrganizationReportForUpdate(recordId);
        scope.organization(user, id(record, "organization_id"));
        if (!record.get("status").equals("SUBMITTED")) {
            throw new Rule("考核材料已审核，请追加新提交");
        }
        return repository.reviewOrganizationReport(
                flag(input, "approve") ? "ACCEPTED" : "RETURNED",
                user.id(),
                text(input, "reason"),
                recordId);
    }

    private Object publishNotice(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "REGULATOR");
        long recordId = number(input, "inspectionId");
        Map<String, Object> record = scope.inspection(user, recordId);
        if (!record.get("status").equals("CLOSED") || record.get("score") == null) {
            throw new Rule("只公示已完成抽查和整改的结果");
        }
        if (repository.countActiveNotices(recordId) > 0) {
            throw new Rule("该抽查结果已公示");
        }
        String body =
                "抽查方式："
                        + (record.get("method").equals("PHONE") ? "电话回访" : "上门核查")
                        + "；评分："
                        + record.get("score")
                        + "；处置状态：已完成。个人身份、住址、健康和投诉内容不予公开。";
        return repository.insertNotice(recordId, user.id(), "养老服务质量抽查结果", body);
    }

    private Object withdrawNotice(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "REGULATOR");
        long recordId = number(input, "noticeId");
        Map<String, Object> record = repository.findNoticeForUpdate(recordId);
        scope.inspection(user, id(record, "inspection_id"));
        if (record.get("withdrawn_at") != null) {
            throw new Rule("公示已撤回");
        }
        return repository.withdrawNotice(text(input, "reason"), recordId);
    }

    private Object inspectTarget(Auth.User user, Map<String, Object> input, boolean order) {
        Auth.role(user, "REGULATOR");
        long target = number(input, order ? "orderId" : "caregiverId");
        if (order) {
            if (!scope.order(user, target).get("status").equals("COMPLETED")) {
                throw new Rule("抽查订单应已完成");
            }
        } else {
            scope.worker(user, target);
        }
        long score = number(input, "score");
        boolean rectify = flag(input, "requiresRectification") || score < 60;
        return repository.insertInspection(
                order,
                target,
                user.id(),
                choice(input, "method", "PHONE", "VISIT"),
                score,
                text(input, "issue"),
                rectify ? text(input, "rectification") : "无需整改",
                rectify ? "OPEN" : "CLOSED",
                rectify ? null : "抽查通过");
    }
}
