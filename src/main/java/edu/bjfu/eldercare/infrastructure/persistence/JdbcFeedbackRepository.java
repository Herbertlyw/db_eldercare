package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.domain.repository.FeedbackRepository;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFeedbackRepository implements FeedbackRepository {

    private final Store db;

    public JdbcFeedbackRepository(Store db) {
        this.db = db;
    }

    @Override
    public long countOrderOrganizationDispatches(Number orderId, Number organizationId) {
        return db.count(
                "SELECT COUNT(*) FROM v_dispatch_context d JOIN caregiver c ON c.id=d.caregiver_id"
                        + " WHERE d.order_id=? AND c.organization_id=?",
                orderId,
                organizationId);
    }

    @Override
    public int assignComplaint(Number organizationId, Number handlerId, Number complaintId) {
        return db.update(
                "UPDATE complaint SET"
                        + " status='ASSIGNED',organization_id=?,handler_id=?,updated_at=NOW() WHERE"
                        + " id=?",
                organizationId,
                handlerId,
                complaintId);
    }

    @Override
    public int replyToComplaint(String result, Number complaintId) {
        return db.update(
                "UPDATE complaint SET status='REPLIED',result=?,updated_at=NOW() WHERE id=?",
                result,
                complaintId);
    }

    @Override
    public int closeComplaint(Number complaintId) {
        return db.update(
                "UPDATE complaint SET status='CLOSED',updated_at=NOW() WHERE id=?", complaintId);
    }

    @Override
    public long appendComplaintLog(
            Number complaintId, Number actorId, String status, String message) {
        return db.insert(
                "INSERT INTO complaint_log(complaint_id,actor_id,status,message) VALUES(?,?,?,?)",
                complaintId,
                actorId,
                status,
                message);
    }

    @Override
    public Map<String, Object> findComplaintForUpdate(Number complaintId) {
        return db.one("SELECT * FROM complaint WHERE id=? FOR UPDATE", complaintId);
    }

    @Override
    public long insertFeedback(
            Number orderId,
            Number accountId,
            Number rating,
            String content,
            String kind,
            String status) {
        return db.insert(
                "INSERT INTO complaint(order_id,account_id,rating,content,kind,status)"
                        + " VALUES(?,?,?,?,?,?)",
                orderId,
                accountId,
                rating,
                content,
                kind,
                status);
    }
}
