package edu.bjfu.eldercare.domain.care;

import edu.bjfu.eldercare.domain.shared.Rule;

/** 终止或到期的照护计划只能通过新版本继续服务。 */
public record CarePlan(long id, Status status) {

    public enum Status {
        ACTIVE,
        PAUSED,
        ENDED,
        EXPIRED
    }

    public void requireStateChangeable() {
        if (status == Status.ENDED || status == Status.EXPIRED) {
            throw new Rule("已终止或到期计划应重新制定");
        }
    }
}
