package edu.bjfu.eldercare.domain.feedback;

import edu.bjfu.eldercare.domain.shared.Rule;

/** 投诉必须按受理、回复、结案的顺序办理。 */
public record Complaint(long id, Status status) {

    public enum Status {
        SUBMITTED,
        ASSIGNED,
        REPLIED,
        CLOSED
    }

    public void requireAssignable() {
        if (status != Status.SUBMITTED) {
            throw new Rule("投诉已受理");
        }
    }

    public void requireReplyable() {
        if (status != Status.ASSIGNED) {
            throw new Rule("投诉不在机构处理中");
        }
    }

    public void requireClosable() {
        if (status != Status.REPLIED) {
            throw new Rule("请先由机构回复");
        }
    }
}
