package edu.bjfu.eldercare.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 业务用例所需的数据操作契约；SQL 与 JDBC 由基础设施实现。 */
public interface GovernanceRepository {

    Map<String, Object> lockInspection(Number inspectionId);

    int reviewRectification(String decision, Number reviewerId, String note, Number inspectionId);

    int updateInspectionResult(String status, String result, Number inspectionId);

    List<Map<String, Object>> findOrderPopulation(
            Number communityId, LocalDateTime startsAt, LocalDateTime endsAt, String seed);

    List<Map<String, Object>> findCaregiverPopulation(
            Number communityId, LocalDateTime startsAt, LocalDateTime endsAt, String seed);

    long insertInspectionBatch(
            Number communityId,
            Number inspectorId,
            String targetType,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Number ratio,
            Number population,
            Number sampleCount,
            String seed);

    long insertSampledInspection(String type, Number targetId, Number inspectorId, Number batchId);

    int recordInspection(
            String method,
            Number score,
            String issue,
            String rectification,
            String status,
            String result,
            Number inspectionId);

    long insertRectification(
            Number inspectionId, Number submittedBy, String measures, String evidence);

    int markInspectionForReview(Number inspectionId);

    long insertOrganizationReport(
            Number organizationId,
            String period,
            String summary,
            String evidence,
            Number submittedBy);

    Map<String, Object> findOrganizationReportForUpdate(Number reportId);

    int reviewOrganizationReport(String status, Number reviewerId, String note, Number reportId);

    long countActiveNotices(Number inspectionId);

    long insertNotice(Number inspectionId, Number publisherId, String title, String body);

    Map<String, Object> findNoticeForUpdate(Number noticeId);

    int withdrawNotice(String reason, Number noticeId);

    long insertInspection(
            boolean order,
            Number targetId,
            Number inspectorId,
            String method,
            Number score,
            String issue,
            String rectification,
            String status,
            String result);
}
