package edu.bjfu.eldercare.domain.repository;

import java.time.LocalDateTime;
import java.util.Map;

/** 业务用例所需的数据操作契约；SQL 与 JDBC 由基础设施实现。 */
public interface SubsidyRepository {

    void submitClaim(Number elderId, Number policyId, String period, String orderIdsJson);

    Map<String, Object> findClaimForUpdate(Number claimId);

    long appendReview(
            Number claimId, Number reviewerId, String stage, String decision, String reason);

    int updateClaimStatus(String status, Number claimId);

    long insertPayment(Number claimId, String referenceNo, Number amount);

    int markPaid(Number claimId);

    long insertEvidence(
            Number claimId, Number submittedBy, String name, String content, String hashContent);

    Map<String, Object> lockClaim(Number claimId);

    int markSubmitted(Number claimId);

    long insertPolicy(
            String name,
            Number communityId,
            Number itemId,
            String abilityLevel,
            Number rate,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            String settlementCycle,
            Number minimumAge,
            String requiredTag);

    long addPolicyArea(Number policyId, Number communityId);
}
