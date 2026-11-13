package edu.bjfu.eldercare.domain.monitoring;

import edu.bjfu.eldercare.domain.shared.Rule;

/** 应急事件须先响应、登记到场，再记录结果关闭。 */
public record EmergencyEvent(long id, Status status, boolean arrived) {
    public enum Status {
        UNCONFIRMED,
        OPEN,
        RESPONDED,
        CLOSED,
        FALSE_ALARM
    }

    public void requireRespondable() {
        require(status == Status.UNCONFIRMED || status == Status.OPEN, "事件已响应或关闭");
    }

    public void requireClosable() {
        require(status == Status.RESPONDED && arrived, "请先响应并登记到场");
    }

    public void requireArrivalRecordable() {
        require(status == Status.RESPONDED && !arrived, "事件不能重复登记到场");
    }

    public void requireConfirmable() {
        require(status == Status.UNCONFIRMED, "事件非待确认");
    }

    public void requireDismissible() {
        require(status == Status.UNCONFIRMED || status == Status.OPEN, "已响应事件应记录处置结果");
    }

    private static void require(boolean allowed, String message) {
        if (!allowed) {
            throw new Rule(message);
        }
    }
}
