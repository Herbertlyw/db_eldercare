package edu.bjfu.eldercare.application.service;

import static edu.bjfu.eldercare.application.support.Inputs.number;
import static edu.bjfu.eldercare.application.support.Inputs.text;
import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.ActionHandler;
import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.domain.feedback.Complaint;
import edu.bjfu.eldercare.domain.order.ServiceOrder;
import edu.bjfu.eldercare.domain.repository.FeedbackRepository;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 服务评价与投诉办理。 */
@Component
public class FeedbackService implements ActionModule {

    private final FeedbackRepository repository;

    private final Scope scope;

    public FeedbackService(FeedbackRepository repository, Scope scope) {
        this.repository = repository;
        this.scope = scope;
    }

    public Map<String, ActionHandler> actions() {
        return Map.ofEntries(
                Map.entry("complaint.create", this::createComplaint),
                Map.entry("review.create", this::createReview),
                Map.entry("complaint.assign", this::assignComplaint),
                Map.entry("complaint.reply", this::replyComplaint),
                Map.entry("complaint.close", this::closeComplaint));
    }

    private Object createComplaint(Auth.User user, Map<String, Object> input) {
        return createFeedback(user, input, false);
    }

    private Object createReview(Auth.User user, Map<String, Object> input) {
        return createFeedback(user, input, true);
    }

    private Object assignComplaint(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "complaintId");
        Map<String, Object> record = complaint(user, recordId);
        complaintState(record).requireAssignable();
        Long org = input.get("organizationId") == null ? null : number(input, "organizationId");
        if (org != null
                && repository.countOrderOrganizationDispatches((Number) record.get("order_id"), org)
                        == 0) {
            throw new Rule("该机构未承接这笔订单");
        }
        repository.assignComplaint(org, user.id(), recordId);
        logComplaint(user, recordId, "ASSIGNED", text(input, "reason"));
        return org == null ? "已由社区受理" : "已交机构处理";
    }

    private Object replyComplaint(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER", "ADMIN");
        long recordId = number(input, "complaintId");
        Map<String, Object> record = complaint(user, recordId);
        if (user.role().equals("ADMIN") && record.get("organization_id") != null) {
            throw new Rule("已转交机构的投诉请由机构回复");
        }
        complaintState(record).requireReplyable();
        repository.replyToComplaint(text(input, "result"), recordId);
        logComplaint(user, recordId, "REPLIED", text(input, "result"));
        return "已提交处理结果";
    }

    private Object closeComplaint(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN", "FAMILY");
        long recordId = number(input, "complaintId");
        Map<String, Object> record = complaint(user, recordId);
        complaintState(record).requireClosable();
        repository.closeComplaint(recordId);
        logComplaint(user, recordId, "CLOSED", text(input, "reason"));
        return "投诉已结案";
    }

    private void logComplaint(Auth.User user, long complaint, String status, String message) {
        repository.appendComplaintLog(complaint, user.id(), status, message);
    }

    private Map<String, Object> complaint(Auth.User user, long recordId) {
        var c = repository.findComplaintForUpdate(recordId);
        scope.order(user, id(c, "order_id"));
        if (user.role().equals("MANAGER")
                && !Objects.equals(c.get("organization_id"), user.organization())) {
            throw new Forbidden();
        }
        return c;
    }

    private Object createFeedback(Auth.User user, Map<String, Object> input, boolean review) {
        Auth.role(user, "FAMILY");
        long recordId = number(input, "orderId");
        var order = scope.order(user, recordId);
        if (review) {
            new ServiceOrder(recordId, ServiceOrder.Status.valueOf((String) order.get("status")))
                    .requireReviewable();
        }
        String status = review ? "CLOSED" : "SUBMITTED";
        long complaint =
                repository.insertFeedback(
                        recordId,
                        user.id(),
                        number(input, "rating"),
                        text(input, "content"),
                        review ? "REVIEW" : "COMPLAINT",
                        status);
        logComplaint(user, complaint, status, text(input, "content"));
        return complaint;
    }

    private Complaint complaintState(Map<String, Object> row) {
        return new Complaint(id(row, "id"), Complaint.Status.valueOf((String) row.get("status")));
    }
}
