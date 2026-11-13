package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.domain.repository.SubsidyRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSubsidyRepository implements SubsidyRepository {

    private final Store db;

    public JdbcSubsidyRepository(Store db) {
        this.db = db;
    }

    @Override
    public void submitClaim(Number elderId, Number policyId, String period, String orderIdsJson) {
        db.call("submit_claim", elderId, policyId, period, orderIdsJson);
    }

    @Override
    public Map<String, Object> findClaimForUpdate(Number claimId) {
        return db.one("SELECT * FROM claim WHERE id=? FOR UPDATE", claimId);
    }

    @Override
    public long appendReview(
            Number claimId, Number reviewerId, String stage, String decision, String reason) {
        return db.insert(
                "INSERT INTO claim_review(claim_id,reviewer_id,stage,decision,reason)"
                        + " VALUES(?,?,?,?,?)",
                claimId,
                reviewerId,
                stage,
                decision,
                reason);
    }

    @Override
    public int updateClaimStatus(String status, Number claimId) {
        return db.update("UPDATE claim SET status=? WHERE id=?", status, claimId);
    }

    @Override
    public long insertPayment(Number claimId, String referenceNo, Number amount) {
        return db.insert(
                "INSERT INTO payment(claim_id,reference_no,amount) VALUES(?,?,?)",
                claimId,
                referenceNo,
                amount);
    }

    @Override
    public int markPaid(Number claimId) {
        return db.update("UPDATE claim SET status='PAID' WHERE id=?", claimId);
    }

    @Override
    public long insertEvidence(
            Number claimId, Number submittedBy, String name, String content, String hashContent) {
        return db.insert(
                "INSERT INTO claim_evidence(claim_id,submitted_by,name,content,content_sha256)"
                        + " VALUES(?,?,?,?,SHA2(?,256))",
                claimId,
                submittedBy,
                name,
                content,
                hashContent);
    }

    @Override
    public Map<String, Object> lockClaim(Number claimId) {
        return db.one("SELECT id FROM claim WHERE id=? FOR UPDATE", claimId);
    }

    @Override
    public int markSubmitted(Number claimId) {
        return db.update("UPDATE claim SET status='SUBMITTED' WHERE id=?", claimId);
    }

    @Override
    public long insertPolicy(
            String name,
            Number communityId,
            Number itemId,
            String abilityLevel,
            Number rate,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String settlementCycle,
            Number minimumAge,
            String requiredTag) {
        return db.insert(
                "INSERT INTO"
                    + " policy(name,community_id,item_id,ability_level,rate,starts_at,ends_at,settlement_cycle,minimum_age,required_tag)"
                    + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                name,
                communityId,
                itemId,
                abilityLevel,
                rate,
                startsAt,
                endsAt,
                settlementCycle,
                minimumAge,
                requiredTag);
    }

    @Override
    public long addPolicyArea(Number policyId, Number communityId) {
        return db.insert(
                "INSERT INTO policy_area(policy_id,community_id) VALUES(?,?)",
                policyId,
                communityId);
    }
}
