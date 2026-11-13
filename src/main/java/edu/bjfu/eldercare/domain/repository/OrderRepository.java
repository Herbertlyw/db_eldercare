package edu.bjfu.eldercare.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 业务用例所需的数据操作契约；SQL 与 JDBC 由基础设施实现。 */
public interface OrderRepository {

    Map<String, Object> findElder(Number elderId);

    Map<String, Object> findServiceItem(Number itemId);

    long insertOrder(
            Number elderId,
            Number itemId,
            Number communityId,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String address,
            String channel,
            Number amount,
            Number createdBy,
            Number x,
            Number y);

    void assignOrder(Number orderId, Number shiftId, String reason);

    Map<String, Object> findWaitingDispatch(Number orderId);

    void assertWorkerEligible(Number orderId, Number shiftId);

    int acceptDispatch(Number dispatchId);

    int markAccepted(Number orderId);

    int recordConfirmation(Number confirmedBy, String confirmation, Number orderId);

    int markCompleted(Number orderId);

    List<Map<String, Object>> findActiveDispatches(Number orderId);

    int rejectDispatch(String reason, Number dispatchId);

    int releaseShiftCapacity(Number shiftId);

    int markCancelled(Number orderId);

    Map<String, Object> lockOrder(Number orderId);

    Map<String, Object> findActiveDispatch(Number orderId);

    int markPending(Number orderId);

    long findFenceRadius(Number communityId);

    Map<String, Object> findAcceptedDispatch(Number orderId);

    long insertCheckin(Number dispatchId, String location, Boolean fenceOk, Number x, Number y);

    int markServing(Number orderId);

    int recordCheckout(Number x, Number y, Boolean fenceOk, Number dispatchId);

    void verifyHours(Number dispatchId);

    long insertServiceRecord(Number dispatchId, String notes);

    int finishDispatch(Number dispatchId);

    int markAwaitingConfirmation(Number orderId);

    void assertOrderEligible(
            Number elderId,
            Number itemId,
            Number communityId,
            LocalDateTime startsAt,
            LocalDateTime endsAt);

    List<Map<String, Object>> findRecommendations(
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Number activeOrderId,
            Number communityId,
            Number itemId,
            LocalDateTime credentialStart,
            LocalDateTime credentialEnd,
            Number excludedOrderId,
            LocalDateTime overlapEnd,
            LocalDateTime overlapStart);
}
