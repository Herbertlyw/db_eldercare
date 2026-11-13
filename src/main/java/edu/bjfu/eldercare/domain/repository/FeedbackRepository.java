package edu.bjfu.eldercare.domain.repository;

import java.util.Map;

/** 业务用例所需的数据操作契约；SQL 与 JDBC 由基础设施实现。 */
public interface FeedbackRepository {

    long countOrderOrganizationDispatches(Number orderId, Number organizationId);

    int assignComplaint(Number organizationId, Number handlerId, Number complaintId);

    int replyToComplaint(String result, Number complaintId);

    int closeComplaint(Number complaintId);

    long appendComplaintLog(Number complaintId, Number actorId, String status, String message);

    Map<String, Object> findComplaintForUpdate(Number complaintId);

    long insertFeedback(
            Number orderId,
            Number accountId,
            Number rating,
            String content,
            String kind,
            String status);
}
