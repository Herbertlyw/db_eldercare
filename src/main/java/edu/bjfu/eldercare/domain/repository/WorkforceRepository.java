package edu.bjfu.eldercare.domain.repository;

import java.time.LocalDateTime;
import java.util.Map;

/** 业务用例所需的数据操作契约；SQL 与 JDBC 由基础设施实现。 */
public interface WorkforceRepository {

    long insertCredential(
            Number caregiverId,
            Number itemId,
            String certificateNo,
            String certificateLevel,
            String validFrom,
            String validUntil);

    int updateCaregiverStatus(String status, Number caregiverId);

    long insertShift(Number caregiverId, String startsAt, String endsAt, Number capacity);

    Map<String, Object> findShiftForUpdate(Number shiftId);

    int deleteFutureShift(Number shiftId);

    Map<String, Object> findAttendanceForUpdate(Number attendanceId);

    long findAttendanceMinutes(Number attendanceId);

    int reviewHours(
            String status, Boolean approve, Number reviewerId, String reason, Number attendanceId);

    Map<String, Object> findCommunity(Number communityId);

    int addServiceArea(Number caregiverId, Number communityId);

    long countActiveDispatchesInArea(Number caregiverId, Number communityId);

    int removeServiceArea(Number caregiverId, Number communityId);

    long insertRecruitment(Number organizationId, String name, String phoneCipher);

    Map<String, Object> findRecruitmentForUpdate(Number recruitmentId);

    int rejectRecruitment(Number reviewerId, String note, Number recruitmentId);

    long findOrganizationCommunity(Number organizationId);

    long insertCaregiverAccount(
            String username, String passwordHash, Number communityId, Number organizationId);

    long insertCaregiver(Number accountId, Number organizationId, String name);

    long insertInitialServiceArea(Number caregiverId, Number communityId);

    int hireRecruitment(Number caregiverId, Number reviewerId, String note, Number recruitmentId);

    long insertTraining(
            Number organizationId, String title, LocalDateTime startsAt, LocalDateTime endsAt);

    long findTrainingOrganization(Number trainingId);

    long enrollTraining(Number trainingId, Number caregiverId);

    Map<String, Object> findTrainingRecordForUpdate(Number recordId);

    long countFinishedTraining(Number trainingId);

    int recordTrainingResult(Number score, String result, Number recordedBy, Number recordId);
}
