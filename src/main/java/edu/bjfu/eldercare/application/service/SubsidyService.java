package edu.bjfu.eldercare.application.service;

import static edu.bjfu.eldercare.application.support.Inputs.choice;
import static edu.bjfu.eldercare.application.support.Inputs.decimal;
import static edu.bjfu.eldercare.application.support.Inputs.ids;
import static edu.bjfu.eldercare.application.support.Inputs.number;
import static edu.bjfu.eldercare.application.support.Inputs.optional;
import static edu.bjfu.eldercare.application.support.Inputs.rawFlag;
import static edu.bjfu.eldercare.application.support.Inputs.rawNumber;
import static edu.bjfu.eldercare.application.support.Inputs.rawText;
import static edu.bjfu.eldercare.application.support.Inputs.text;
import static edu.bjfu.eldercare.application.support.Inputs.time;
import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.ActionHandler;
import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.RecordAccess;
import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.domain.repository.SubsidyRepository;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.domain.subsidy.SubsidyClaim;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 政策、补贴申报、审核和模拟拨付。 */
@Component
public class SubsidyService implements ActionModule {

    private final SubsidyRepository repository;

    private final Scope scope;

    private final RecordAccess access;

    public SubsidyService(SubsidyRepository repository, Scope scope, RecordAccess access) {
        this.repository = repository;
        this.scope = scope;
        this.access = access;
    }

    public Map<String, ActionHandler> actions() {
        return Map.ofEntries(
                Map.entry("claim.submit", this::submitClaim),
                Map.entry("claim.review", this::reviewClaim),
                Map.entry("claim.pay", this::payClaim),
                Map.entry("policy.create", this::createPolicy),
                Map.entry("claim.evidence", this::addClaimEvidence),
                Map.entry("claim.resubmit", this::resubmitClaim));
    }

    private Object submitClaim(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = rawNumber(input, "elderId");
        access.elder(user, recordId);
        try {
            repository.submitClaim(
                    recordId,
                    rawNumber(input, "policyId"),
                    rawText(input, "period"),
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(input.get("orderIds")));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new Rule("订单列表无效");
        }
        return "申报成功";
    }

    private Object reviewClaim(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "STREET", "REGULATOR");
        long recordId = rawNumber(input, "claimId");
        var claim = repository.findClaimForUpdate(recordId);
        scope.claim(user, recordId);
        boolean approve = rawFlag(input, "approve");
        var next = claimState(claim).review(SubsidyClaim.ReviewStage.valueOf(user.role()), approve);
        repository.appendReview(
                recordId,
                user.id(),
                user.role(),
                approve ? "APPROVE" : "REJECT",
                rawText(input, "reason"));
        return repository.updateClaimStatus(next.name(), recordId);
    }

    private Object payClaim(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "REGULATOR");
        long recordId = rawNumber(input, "claimId");
        var claim = repository.findClaimForUpdate(recordId);
        scope.claim(user, recordId);
        claimState(claim).requirePayable();
        repository.insertPayment(
                recordId, rawText(input, "reference"), (Number) claim.get("amount"));
        return repository.markPaid(recordId);
    }

    private Object createPolicy(Auth.User user, Map<String, Object> input) {
        return policy(user, input);
    }

    private Object addClaimEvidence(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "claimId");
        scope.claim(user, recordId);
        String content = text(input, "content");
        return repository.insertEvidence(
                recordId, user.id(), text(input, "name"), content, content);
    }

    private Object resubmitClaim(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "claimId");
        Map<String, Object> record = scope.claim(user, recordId);
        repository.lockClaim(recordId);
        claimState(record).requireResubmittable();
        addClaimEvidence(
                user,
                Map.of("claimId", recordId, "name", "重新提交说明", "content", text(input, "reason")));
        return repository.markSubmitted(recordId);
    }

    private Object policy(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "REGULATOR");
        List<Long> areas = ids(input, "communityIds");
        for (long c : areas) {
            scope.community(user, c);
        }
        long policyId =
                repository.insertPolicy(
                        text(input, "name"),
                        user.community(),
                        number(input, "itemId"),
                        choice(input, "level", "SELF", "PARTIAL", "SEVERE"),
                        decimal(input, "rate"),
                        time(input, "startsAt"),
                        time(input, "endsAt"),
                        choice(input, "cycle", "MONTH", "QUARTER", "YEAR"),
                        number(input, "minimumAge"),
                        optional(input, "tag", "NONE").equals("NONE")
                                ? null
                                : choice(input, "tag", "ALONE", "DISABLED", "LOW_INCOME"));
        for (long c : areas) {
            repository.addPolicyArea(policyId, c);
        }
        return policyId;
    }

    private SubsidyClaim claimState(Map<String, Object> row) {
        return new SubsidyClaim(
                id(row, "id"), SubsidyClaim.Status.valueOf((String) row.get("status")));
    }
}
