package edu.bjfu.eldercare.infrastructure.persistence;

import edu.bjfu.eldercare.domain.repository.WorkforceRepository;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkforceRepository implements WorkforceRepository {

    private final Store db;

    public JdbcWorkforceRepository(Store db) {
        this.db = db;
    }

    @Override
    public long insertCredential(
            Number caregiverId,
            Number itemId,
            String certificateNo,
            String certificateLevel,
            String validFrom,
            String validUntil) {
        return db.insert(
                "INSERT INTO"
                    + " credential(caregiver_id,item_id,certificate_no,certificate_level,valid_from,valid_until)"
                    + " VALUES(?,?,?,?,?,?)",
                caregiverId,
                itemId,
                certificateNo,
                certificateLevel,
                validFrom,
                validUntil);
    }

    @Override
    public int updateCaregiverStatus(String status, Number caregiverId) {
        return db.update("UPDATE caregiver SET status=? WHERE id=?", status, caregiverId);
    }

    @Override
    public long insertShift(Number caregiverId, String startsAt, String endsAt, Number capacity) {
        return db.insert(
                "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity) VALUES(?,?,?,?)",
                caregiverId,
                startsAt,
                endsAt,
                capacity);
    }

    @Override
    public Map<String, Object> findShiftForUpdate(Number shiftId) {
        return db.one("SELECT * FROM shift WHERE id=? FOR UPDATE", shiftId);
    }

    @Override
    public int deleteFutureShift(Number shiftId) {
        return db.update("DELETE FROM shift WHERE id=? AND starts_at>NOW()", shiftId);
    }

    @Override
    public Map<String, Object> findAttendanceForUpdate(Number attendanceId) {
        return db.one(
                "SELECT a.*,d.caregiver_id FROM attendance a JOIN v_dispatch_context d ON"
                        + " d.id=a.dispatch_id WHERE a.id=? FOR UPDATE",
                attendanceId);
    }

    @Override
    public long findAttendanceMinutes(Number attendanceId) {
        return db.count(
                "SELECT TIMESTAMPDIFF(MINUTE,check_in,check_out) FROM attendance WHERE id=?",
                attendanceId);
    }

    @Override
    public int reviewHours(
            String status, Boolean approve, Number reviewerId, String reason, Number attendanceId) {
        return db.update(
                "UPDATE attendance SET"
                    + " status=?,effective_minutes=IF(?,TIMESTAMPDIFF(MINUTE,check_in,check_out),0),reviewer_id=?,review_reason=?"
                    + " WHERE id=?",
                status,
                approve,
                reviewerId,
                reason,
                attendanceId);
    }

    @Override
    public Map<String, Object> findCommunity(Number communityId) {
        return db.one("SELECT id FROM community WHERE id=?", communityId);
    }

    @Override
    public int addServiceArea(Number caregiverId, Number communityId) {
        return db.update(
                "INSERT IGNORE INTO caregiver_area(caregiver_id,community_id) VALUES(?,?)",
                caregiverId,
                communityId);
    }

    @Override
    public long countActiveDispatchesInArea(Number caregiverId, Number communityId) {
        return db.count(
                "SELECT COUNT(*) FROM v_dispatch_context d JOIN service_order o ON o.id=d.order_id"
                        + " WHERE d.caregiver_id=? AND o.community_id=? AND d.status"
                        + " IN('WAITING','ACCEPTED')",
                caregiverId,
                communityId);
    }

    @Override
    public int removeServiceArea(Number caregiverId, Number communityId) {
        return db.update(
                "DELETE FROM caregiver_area WHERE caregiver_id=? AND community_id=?",
                caregiverId,
                communityId);
    }

    @Override
    public long insertRecruitment(Number organizationId, String name, String phoneCipher) {
        return db.insert(
                "INSERT INTO recruitment(organization_id,candidate_name,phone_cipher)"
                        + " VALUES(?,?,?)",
                organizationId,
                name,
                phoneCipher);
    }

    @Override
    public Map<String, Object> findRecruitmentForUpdate(Number recruitmentId) {
        return db.one("SELECT * FROM recruitment WHERE id=? FOR UPDATE", recruitmentId);
    }

    @Override
    public int rejectRecruitment(Number reviewerId, String note, Number recruitmentId) {
        return db.update(
                "UPDATE recruitment SET"
                    + " status='REJECTED',reviewer_id=?,review_note=?,reviewed_at=NOW() WHERE id=?",
                reviewerId,
                note,
                recruitmentId);
    }

    @Override
    public long findOrganizationCommunity(Number organizationId) {
        return db.count("SELECT community_id FROM organization WHERE id=?", organizationId);
    }

    @Override
    public long insertCaregiverAccount(
            String username, String passwordHash, Number communityId, Number organizationId) {
        return db.insert(
                "INSERT INTO account(username,password_hash,role,community_id,organization_id)"
                        + " VALUES(?,?,'CAREGIVER',?,?)",
                username,
                passwordHash,
                communityId,
                organizationId);
    }

    @Override
    public long insertCaregiver(Number accountId, Number organizationId, String name) {
        return db.insert(
                "INSERT INTO caregiver(account_id,organization_id,name) VALUES(?,?,?)",
                accountId,
                organizationId,
                name);
    }

    @Override
    public long insertInitialServiceArea(Number caregiverId, Number communityId) {
        return db.insert(
                "INSERT INTO caregiver_area(caregiver_id,community_id) VALUES(?,?)",
                caregiverId,
                communityId);
    }

    @Override
    public int hireRecruitment(
            Number caregiverId, Number reviewerId, String note, Number recruitmentId) {
        return db.update(
                "UPDATE recruitment SET"
                    + " status='HIRED',caregiver_id=?,reviewer_id=?,review_note=?,reviewed_at=NOW()"
                    + " WHERE id=?",
                caregiverId,
                reviewerId,
                note,
                recruitmentId);
    }

    @Override
    public long insertTraining(
            Number organizationId, String title, LocalDateTime startsAt, LocalDateTime endsAt) {
        return db.insert(
                "INSERT INTO training(organization_id,title,starts_at,ends_at) VALUES(?,?,?,?)",
                organizationId,
                title,
                startsAt,
                endsAt);
    }

    @Override
    public long findTrainingOrganization(Number trainingId) {
        return db.count("SELECT organization_id FROM training WHERE id=?", trainingId);
    }

    @Override
    public long enrollTraining(Number trainingId, Number caregiverId) {
        return db.insert(
                "INSERT INTO training_record(training_id,caregiver_id) VALUES(?,?)",
                trainingId,
                caregiverId);
    }

    @Override
    public Map<String, Object> findTrainingRecordForUpdate(Number recordId) {
        return db.one(
                "SELECT r.*,t.organization_id,t.ends_at FROM training_record r JOIN training t ON"
                        + " t.id=r.training_id WHERE r.id=? FOR UPDATE",
                recordId);
    }

    @Override
    public long countFinishedTraining(Number trainingId) {
        return db.count("SELECT COUNT(*) FROM training WHERE id=? AND ends_at<=NOW()", trainingId);
    }

    @Override
    public int recordTrainingResult(
            Number score, String result, Number recordedBy, Number recordId) {
        return db.update(
                "UPDATE training_record SET score=?,result=?,recorded_by=?,recorded_at=NOW() WHERE"
                        + " id=?",
                score,
                result,
                recordedBy,
                recordId);
    }
}
