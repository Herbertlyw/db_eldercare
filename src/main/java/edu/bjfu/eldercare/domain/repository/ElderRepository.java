package edu.bjfu.eldercare.domain.repository;

import java.time.LocalDateTime;
import java.util.Map;

/** 业务用例所需的数据操作契约；SQL 与 JDBC 由基础设施实现。 */
public interface ElderRepository {

    long insertElder(
            String name,
            String identityHash,
            String identityCipher,
            String birthDate,
            String address,
            Number communityId,
            Number x,
            Number y);

    long countFamilyAccounts(Number accountId, Number communityId);

    long insertGuardian(Number elderId, Number accountId, String relationName, String phoneCipher);

    long countUnfinishedOrders(Number elderId);

    int pauseActivePlans(Number elderId);

    int deactivateElder(Number elderId);

    long insertAssessment(
            Number elderId,
            String scaleName,
            Number score,
            String abilityLevel,
            Number assessorId,
            String validUntil);

    long insertFamilyAccount(String username, String passwordHash, Number communityId);

    int addTag(Number elderId, String tag);

    int removeTag(Number elderId, String tag);

    Map<String, Object> findGuardianForUpdate(Number guardianId);

    int updateGuardian(String relationName, String phoneCipher, Boolean active, Number guardianId);

    Map<String, Object> findPlanForUpdate(Number planId);

    int updatePlanStatus(String status, Number planId);

    long appendPlanStateReason(Number planId, String actor, String reason);

    long insertServiceItem(
            String name,
            String category,
            String unit,
            Number price,
            Number durationMinutes,
            Boolean subsidizable);

    long insertBundle(String name, String abilityLevel);

    long insertBundleItem(Number bundleId, Number itemId, Number weeklyFrequency);

    Map<String, Object> findCommunity(Number communityId);

    Map<String, Object> lockElder(Number elderId);

    long countAssessmentsForElder(Number assessmentId, Number elderId);

    int endPlan(Number planId);

    long insertPlan(
            Number assessmentId,
            Number bundleId,
            Number caregiverId,
            LocalDateTime startsAt,
            LocalDateTime endsAt);

    long countBundleItems(Number bundleId, Number itemId);

    long insertPlanItem(Number planId, Number itemId, Number weeklyFrequency);

    int copyBundleItems(Number planId, Number bundleId);

    long countPlanItems(Number planId);

    long appendPlanRevision(Number planId, String actor, Number previousPlanId, String reason);

    long countActiveDeviceBindings(Number elderId);

    int updateAddress(String address, Number communityId, Number x, Number y, Number elderId);
}
