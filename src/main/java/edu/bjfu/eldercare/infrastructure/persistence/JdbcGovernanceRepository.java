package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.domain.repository.GovernanceRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGovernanceRepository implements GovernanceRepository {

    private final Store db;

    public JdbcGovernanceRepository(Store db) {
        this.db = db;
    }

    @Override
    public Map<String, Object> lockInspection(Number inspectionId) {
        return db.one("SELECT id FROM inspection WHERE id=? FOR UPDATE", inspectionId);
    }

    @Override
    public int reviewRectification(
            String decision, Number reviewerId, String note, Number inspectionId) {
        return db.update(
                "UPDATE rectification_submission SET"
                        + " decision=?,reviewer_id=?,review_note=?,reviewed_at=NOW() WHERE"
                        + " inspection_id=? AND decision='PENDING'",
                decision,
                reviewerId,
                note,
                inspectionId);
    }

    @Override
    public int updateInspectionResult(String status, String result, Number inspectionId) {
        return db.update(
                "UPDATE inspection SET status=?,result=? WHERE id=?", status, result, inspectionId);
    }

    @Override
    public List<Map<String, Object>> findOrderPopulation(
            Number communityId, LocalDateTime startsAt, LocalDateTime endsAt, String seed) {
        return db.rows(
                "SELECT id FROM service_order WHERE community_id=? AND status='COMPLETED' AND"
                        + " starts_at>=? AND starts_at<? ORDER BY SHA2(CONCAT(id,?),256)",
                communityId,
                startsAt,
                endsAt,
                seed);
    }

    @Override
    public List<Map<String, Object>> findCaregiverPopulation(
            Number communityId, LocalDateTime startsAt, LocalDateTime endsAt, String seed) {
        return db.rows(
                "SELECT DISTINCT c.id FROM caregiver c JOIN v_dispatch_context d ON"
                        + " d.caregiver_id=c.id JOIN service_order o ON o.id=d.order_id WHERE"
                        + " o.community_id=? AND o.status='COMPLETED' AND d.status='FINISHED' AND"
                        + " o.starts_at>=? AND o.starts_at<? ORDER BY SHA2(CONCAT(c.id,?),256)",
                communityId,
                startsAt,
                endsAt,
                seed);
    }

    @Override
    public long insertInspectionBatch(
            Number communityId,
            Number inspectorId,
            String targetType,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Number ratio,
            Number population,
            Number sampleCount,
            String seed) {
        return db.insert(
                "INSERT INTO"
                    + " inspection_batch(community_id,inspector_id,target_type,starts_at,ends_at,ratio,population,sample_count,sample_seed)"
                    + " VALUES(?,?,?,?,?,?,?,?,?)",
                communityId,
                inspectorId,
                targetType,
                startsAt,
                endsAt,
                ratio,
                population,
                sampleCount,
                seed);
    }

    @Override
    public long insertSampledInspection(
            String type, Number targetId, Number inspectorId, Number batchId) {
        return db.insert(
                "INSERT INTO inspection("
                        + (type.equals("ORDER") ? "order_id" : "caregiver_id")
                        + ",inspector_id,method,score,issue,rectification,status,batch_id)"
                        + " VALUES(?,?,'PHONE',NULL,'待抽查','待确定','PENDING',?)",
                targetId,
                inspectorId,
                batchId);
    }

    @Override
    public int recordInspection(
            String method,
            Number score,
            String issue,
            String rectification,
            String status,
            String result,
            Number inspectionId) {
        return db.update(
                "UPDATE inspection SET method=?,score=?,issue=?,rectification=?,status=?,result=?"
                        + " WHERE id=?",
                method,
                score,
                issue,
                rectification,
                status,
                result,
                inspectionId);
    }

    @Override
    public long insertRectification(
            Number inspectionId, Number submittedBy, String measures, String evidence) {
        return db.insert(
                "INSERT INTO rectification_submission(inspection_id,submitted_by,measures,evidence)"
                        + " VALUES(?,?,?,?)",
                inspectionId,
                submittedBy,
                measures,
                evidence);
    }

    @Override
    public int markInspectionForReview(Number inspectionId) {
        return db.update("UPDATE inspection SET status='REVIEW' WHERE id=?", inspectionId);
    }

    @Override
    public long insertOrganizationReport(
            Number organizationId,
            String period,
            String summary,
            String evidence,
            Number submittedBy) {
        return db.insert(
                "INSERT INTO"
                    + " organization_report(organization_id,period,summary,evidence,submitted_by)"
                    + " VALUES(?,?,?,?,?)",
                organizationId,
                period,
                summary,
                evidence,
                submittedBy);
    }

    @Override
    public Map<String, Object> findOrganizationReportForUpdate(Number reportId) {
        return db.one("SELECT * FROM organization_report WHERE id=? FOR UPDATE", reportId);
    }

    @Override
    public int reviewOrganizationReport(
            String status, Number reviewerId, String note, Number reportId) {
        return db.update(
                "UPDATE organization_report SET"
                        + " status=?,reviewer_id=?,review_note=?,reviewed_at=NOW() WHERE id=?",
                status,
                reviewerId,
                note,
                reportId);
    }

    @Override
    public long countActiveNotices(Number inspectionId) {
        return db.count(
                "SELECT COUNT(*) FROM public_notice WHERE inspection_id=? AND withdrawn_at IS NULL",
                inspectionId);
    }

    @Override
    public long insertNotice(Number inspectionId, Number publisherId, String title, String body) {
        return db.insert(
                "INSERT INTO public_notice(inspection_id,publisher_id,title,body) VALUES(?,?,?,?)",
                inspectionId,
                publisherId,
                title,
                body);
    }

    @Override
    public Map<String, Object> findNoticeForUpdate(Number noticeId) {
        return db.one("SELECT * FROM public_notice WHERE id=? FOR UPDATE", noticeId);
    }

    @Override
    public int withdrawNotice(String reason, Number noticeId) {
        return db.update(
                "UPDATE public_notice SET withdrawn_at=NOW(),withdraw_reason=? WHERE id=?",
                reason,
                noticeId);
    }

    @Override
    public long insertInspection(
            boolean order,
            Number targetId,
            Number inspectorId,
            String method,
            Number score,
            String issue,
            String rectification,
            String status,
            String result) {
        return db.insert(
                "INSERT INTO inspection("
                        + (order ? "order_id" : "caregiver_id")
                        + ",inspector_id,method,score,issue,rectification,status,result)"
                        + " VALUES(?,?,?,?,?,?,?,?)",
                targetId,
                inspectorId,
                method,
                score,
                issue,
                rectification,
                status,
                result);
    }
}
