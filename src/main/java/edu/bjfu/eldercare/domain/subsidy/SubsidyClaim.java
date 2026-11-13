package edu.bjfu.eldercare.domain.subsidy;

import edu.bjfu.eldercare.domain.shared.Rule;

/** 申报的分级审核和拨付状态规则，不接触数据库。 */
public record SubsidyClaim(long id, Status status) {

    public enum Status {
        SUBMITTED,
        STREET_APPROVED,
        APPROVED,
        REJECTED,
        PAID
    }

    public enum ReviewStage {
        STREET,
        REGULATOR
    }

    public Status review(ReviewStage stage, boolean approve) {
        Status required = stage == ReviewStage.STREET ? Status.SUBMITTED : Status.STREET_APPROVED;
        if (status != required) {
            throw new Rule("审核阶段不匹配");
        }
        return !approve
                ? Status.REJECTED
                : stage == ReviewStage.STREET ? Status.STREET_APPROVED : Status.APPROVED;
    }

    public void requirePayable() {
        if (status != Status.APPROVED) {
            throw new Rule("申报尚未审批完成");
        }
    }

    public void requireResubmittable() {
        if (status != Status.REJECTED) {
            throw new Rule("只有驳回申报可补资料重报");
        }
    }
}
