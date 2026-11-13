package edu.bjfu.eldercare.domain.order;

import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.Set;

/** 服务订单的状态规则。持久化、权限和事务由外围层负责。 */
public record ServiceOrder(long id, Status status) {

    public enum Status {
        PENDING,
        ASSIGNED,
        ACCEPTED,
        SERVING,
        CONFIRM,
        COMPLETED,
        CANCELLED
    }

    public void requireAcceptable() {
        require(status == Status.ASSIGNED, "订单不可接单");
    }

    public void requireConfirmable() {
        require(status == Status.CONFIRM, "订单非待确认");
    }

    public void requireCancellable() {
        require(
                Set.of(Status.PENDING, Status.ASSIGNED, Status.ACCEPTED).contains(status),
                "当前状态不能取消");
    }

    public void requireDispatchable() {
        require(
                Set.of(Status.PENDING, Status.ASSIGNED, Status.ACCEPTED).contains(status),
                "当前订单无需派单");
    }

    public void requireReleasable() {
        require(Set.of(Status.ASSIGNED, Status.ACCEPTED).contains(status), "当前订单不能退回调度");
    }

    public void requireServiceStage(boolean finish) {
        require(status == (finish ? Status.SERVING : Status.ACCEPTED), "订单不在当前服务阶段");
    }

    public void requireReviewable() {
        require(status == Status.COMPLETED, "服务完成后才能评价");
    }

    private static void require(boolean valid, String message) {
        if (!valid) {
            throw new Rule(message);
        }
    }
}
