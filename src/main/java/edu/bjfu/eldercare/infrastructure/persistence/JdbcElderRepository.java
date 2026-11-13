package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.domain.repository.ElderRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcElderRepository implements ElderRepository {

    private final Store db;

    public JdbcElderRepository(Store db) {
        this.db = db;
    }

    @Override
    public long insertElder(
            String name,
            String identityHash,
            String identityCipher,
            String birthDate,
            String address,
            Number communityId,
            Number x,
            Number y) {
        return db.insert(
                "INSERT INTO"
                    + " elder(name,identity_hash,identity_cipher,birth_date,address,community_id,position_x,position_y)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                name,
                identityHash,
                identityCipher,
                birthDate,
                address,
                communityId,
                x,
                y);
    }

    @Override
    public long countFamilyAccounts(Number accountId, Number communityId) {
        return db.count(
                "SELECT COUNT(*) FROM account WHERE id=? AND role='FAMILY' AND community_id=?",
                accountId,
                communityId);
    }

    @Override
    public long insertGuardian(
            Number elderId, Number accountId, String relationName, String phoneCipher) {
        return db.insert(
                "INSERT INTO guardian(elder_id,account_id,relation_name,phone_cipher)"
                        + " VALUES(?,?,?,?)",
                elderId,
                accountId,
                relationName,
                phoneCipher);
    }

    @Override
    public long countUnfinishedOrders(Number elderId) {
        return db.count(
                "SELECT COUNT(*) FROM service_order WHERE elder_id=? AND status NOT"
                        + " IN('COMPLETED','CANCELLED')",
                elderId);
    }

    @Override
    public int pauseActivePlans(Number elderId) {
        return db.update(
                "UPDATE care_plan p JOIN assessment a ON a.id=p.assessment_id SET p.status='PAUSED'"
                        + " WHERE a.elder_id=? AND p.status='ACTIVE'",
                elderId);
    }

    @Override
    public int deactivateElder(Number elderId) {
        return db.update("UPDATE elder SET status='INACTIVE' WHERE id=?", elderId);
    }

    @Override
    public long insertAssessment(
            Number elderId,
            String scaleName,
            Number score,
            String abilityLevel,
            Number assessorId,
            String validUntil) {
        return db.insert(
                "INSERT INTO"
                    + " assessment(elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until)"
                    + " VALUES(?,?,?,?,?,NOW(),?)",
                elderId,
                scaleName,
                score,
                abilityLevel,
                assessorId,
                validUntil);
    }

    @Override
    public long insertFamilyAccount(String username, String passwordHash, Number communityId) {
        return db.insert(
                "INSERT INTO account(username,password_hash,role,community_id)"
                        + " VALUES(?,?,'FAMILY',?)",
                username,
                passwordHash,
                communityId);
    }

    @Override
    public int addTag(Number elderId, String tag) {
        return db.update("INSERT IGNORE INTO elder_tag(elder_id,tag) VALUES(?,?)", elderId, tag);
    }

    @Override
    public int removeTag(Number elderId, String tag) {
        return db.update("DELETE FROM elder_tag WHERE elder_id=? AND tag=?", elderId, tag);
    }

    @Override
    public Map<String, Object> findGuardianForUpdate(Number guardianId) {
        return db.one("SELECT * FROM guardian WHERE id=? FOR UPDATE", guardianId);
    }

    @Override
    public int updateGuardian(
            String relationName, String phoneCipher, Boolean active, Number guardianId) {
        return db.update(
                "UPDATE guardian SET relation_name=?,phone_cipher=?,active=? WHERE id=?",
                relationName,
                phoneCipher,
                active,
                guardianId);
    }

    @Override
    public Map<String, Object> findPlanForUpdate(Number planId) {
        return db.one("SELECT * FROM v_plan_context WHERE id=? FOR UPDATE", planId);
    }

    @Override
    public int updatePlanStatus(String status, Number planId) {
        return db.update("UPDATE care_plan SET status=? WHERE id=?", status, planId);
    }

    @Override
    public long appendPlanStateReason(Number planId, String actor, String reason) {
        return db.insert(
                "INSERT INTO audit_log(table_name,row_id,operation,actor,after_data)"
                        + " VALUES('care_plan',?,'STATE_REASON',?,JSON_OBJECT('reason',?))",
                planId,
                actor,
                reason);
    }

    @Override
    public long insertServiceItem(
            String name,
            String category,
            String unit,
            Number price,
            Number durationMinutes,
            Boolean subsidizable) {
        return db.insert(
                "INSERT INTO service_item(name,category,unit,price,duration_minutes,subsidizable)"
                        + " VALUES(?,?,?,?,?,?)",
                name,
                category,
                unit,
                price,
                durationMinutes,
                subsidizable);
    }

    @Override
    public long insertBundle(String name, String abilityLevel) {
        return db.insert(
                "INSERT INTO care_bundle(name,ability_level) VALUES(?,?)", name, abilityLevel);
    }

    @Override
    public long insertBundleItem(Number bundleId, Number itemId, Number weeklyFrequency) {
        return db.insert(
                "INSERT INTO bundle_item(bundle_id,item_id,weekly_frequency) VALUES(?,?,?)",
                bundleId,
                itemId,
                weeklyFrequency);
    }

    @Override
    public Map<String, Object> findCommunity(Number communityId) {
        return db.one("SELECT * FROM community WHERE id=?", communityId);
    }

    @Override
    public Map<String, Object> lockElder(Number elderId) {
        return db.one("SELECT id FROM elder WHERE id=? FOR UPDATE", elderId);
    }

    @Override
    public long countAssessmentsForElder(Number assessmentId, Number elderId) {
        return db.count(
                "SELECT COUNT(*) FROM assessment WHERE id=? AND elder_id=?", assessmentId, elderId);
    }

    @Override
    public int endPlan(Number planId) {
        return db.update("UPDATE care_plan SET status='ENDED' WHERE id=?", planId);
    }

    @Override
    public long insertPlan(
            Number assessmentId,
            Number bundleId,
            Number caregiverId,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
        return db.insert(
                "INSERT INTO care_plan(assessment_id,bundle_id,caregiver_id,starts_at,ends_at)"
                        + " VALUES(?,?,?,?,?)",
                assessmentId,
                bundleId,
                caregiverId,
                startsAt,
                endsAt);
    }

    @Override
    public long countBundleItems(Number bundleId, Number itemId) {
        return db.count(
                "SELECT COUNT(*) FROM bundle_item WHERE bundle_id=? AND item_id=?",
                bundleId,
                itemId);
    }

    @Override
    public long insertPlanItem(Number planId, Number itemId, Number weeklyFrequency) {
        return db.insert(
                "INSERT INTO plan_item(plan_id,item_id,weekly_frequency) VALUES(?,?,?)",
                planId,
                itemId,
                weeklyFrequency);
    }

    @Override
    public int copyBundleItems(Number planId, Number bundleId) {
        return db.update(
                "INSERT INTO plan_item(plan_id,item_id,weekly_frequency) SELECT"
                        + " ?,item_id,weekly_frequency FROM bundle_item WHERE bundle_id=?",
                planId,
                bundleId);
    }

    @Override
    public long countPlanItems(Number planId) {
        return db.count("SELECT COUNT(*) FROM plan_item WHERE plan_id=?", planId);
    }

    @Override
    public long appendPlanRevision(
            Number planId, String actor, Number previousPlanId, String reason) {
        return db.insert(
                "INSERT INTO audit_log(table_name,row_id,operation,actor,after_data)"
                    + " VALUES('care_plan',?,'REVISE',?,JSON_OBJECT('previous_plan',?,'reason',?))",
                planId,
                actor,
                previousPlanId,
                reason);
    }

    @Override
    public long countActiveDeviceBindings(Number elderId) {
        return db.count(
                "SELECT COUNT(*) FROM device_binding WHERE elder_id=? AND unbound_at IS NULL",
                elderId);
    }

    @Override
    public int updateAddress(
            String address, Number communityId, Number x, Number y, Number elderId) {
        return db.update(
                "UPDATE elder SET address=?,community_id=?,position_x=?,position_y=? WHERE id=?",
                address,
                communityId,
                x,
                y,
                elderId);
    }
}
