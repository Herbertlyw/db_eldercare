package edu.bjfu.eldercare.application.service;

import static edu.bjfu.eldercare.application.support.Inputs.flag;
import static edu.bjfu.eldercare.application.support.Inputs.number;
import static edu.bjfu.eldercare.application.support.Inputs.rawFlag;
import static edu.bjfu.eldercare.application.support.Inputs.rawNumber;
import static edu.bjfu.eldercare.application.support.Inputs.rawText;
import static edu.bjfu.eldercare.application.support.Inputs.text;
import static edu.bjfu.eldercare.application.support.Inputs.time;
import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.ActionHandler;
import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.Crypto;
import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.AccountPasswords;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.domain.repository.WorkforceRepository;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 招聘、培训、资质、排班与工时。 */
@Component
public class WorkforceService implements ActionModule {

    private final WorkforceRepository repository;

    private final Scope scope;

    private final Crypto crypto;

    private final AccountPasswords passwords;

    public WorkforceService(
            WorkforceRepository repository,
            Scope scope,
            Crypto crypto,
            AccountPasswords passwords) {
        this.repository = repository;
        this.scope = scope;
        this.crypto = crypto;
        this.passwords = passwords;
    }

    public Map<String, ActionHandler> actions() {
        return Map.ofEntries(
                Map.entry("credential.create", this::createCredential),
                Map.entry("caregiver.status", this::changeCaregiverStatus),
                Map.entry("shift.create", this::createShift),
                Map.entry("shift.delete", this::deleteShift),
                Map.entry("hours.review", this::reviewHours),
                Map.entry("caregiver.area", this::setServiceArea),
                Map.entry("recruitment.create", this::createRecruitment),
                Map.entry("recruitment.review", this::reviewRecruitment),
                Map.entry("training.create", this::createTraining),
                Map.entry("training.enroll", this::enrollTraining),
                Map.entry("training.result", this::recordTrainingResult));
    }

    private Object createCredential(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long recordId = rawNumber(input, "caregiverId");
        scope.worker(user, recordId);
        return repository.insertCredential(
                recordId,
                rawNumber(input, "itemId"),
                rawText(input, "certificateNo"),
                rawText(input, "level"),
                rawText(input, "validFrom"),
                rawText(input, "validUntil"));
    }

    private Object changeCaregiverStatus(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long recordId = rawNumber(input, "caregiverId");
        scope.worker(user, recordId);
        return repository.updateCaregiverStatus(rawText(input, "status"), recordId);
    }

    private Object createShift(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER", "ADMIN");
        long recordId = rawNumber(input, "caregiverId");
        scope.worker(user, recordId);
        return repository.insertShift(
                recordId,
                rawText(input, "startsAt"),
                rawText(input, "endsAt"),
                rawNumber(input, "capacity"));
    }

    private Object deleteShift(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long recordId = rawNumber(input, "shiftId");
        var sh = repository.findShiftForUpdate(recordId);
        scope.worker(user, id(sh, "caregiver_id"));
        if (id(sh, "used_count") != 0) {
            throw new Rule("已被使用的班次不能删除");
        }
        return repository.deleteFutureShift(recordId);
    }

    private Object reviewHours(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long recordId = rawNumber(input, "attendanceId");
        var a = repository.findAttendanceForUpdate(recordId);
        scope.worker(user, id(a, "caregiver_id"));
        if (!a.get("status").equals("REVIEW")) {
            throw new Rule("非待复核工时");
        }
        if (rawFlag(input, "approve") && repository.findAttendanceMinutes(recordId) <= 0) {
            throw new Rule("有效服务时长必须大于零");
        }
        return repository.reviewHours(
                rawFlag(input, "approve") ? "VALID" : "INVALID",
                rawFlag(input, "approve"),
                user.id(),
                rawText(input, "reason"),
                recordId);
    }

    private Object setServiceArea(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long recordId = number(input, "caregiverId");
        scope.worker(user, recordId);
        long community = number(input, "communityId");
        repository.findCommunity(community);
        if (flag(input, "enabled")) {
            return repository.addServiceArea(recordId, community);
        }
        if (repository.countActiveDispatchesInArea(recordId, community) > 0) {
            throw new Rule("该区域还有未完成派单");
        }
        return repository.removeServiceArea(recordId, community);
    }

    private Object createRecruitment(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long org = user.organization();
        return repository.insertRecruitment(
                org, text(input, "name"), crypto.encrypt(text(input, "phone")));
    }

    private Object reviewRecruitment(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long org = user.organization();
        long id = number(input, "recruitmentId");
        var record = repository.findRecruitmentForUpdate(id);
        scope.organization(user, id(record, "organization_id"));
        if (!record.get("status").equals("APPLIED")) {
            throw new Rule("招聘申请已经处理");
        }
        if (!flag(input, "approve")) {
            repository.rejectRecruitment(user.id(), text(input, "reason"), id);
            return "未录用";
        }
        long community = repository.findOrganizationCommunity(org);
        long account =
                repository.insertCaregiverAccount(
                        text(input, "username"), passwords.encode(input), community, org);
        long worker =
                repository.insertCaregiver(account, org, (String) record.get("candidate_name"));
        repository.insertInitialServiceArea(worker, community);
        repository.hireRecruitment(worker, user.id(), text(input, "reason"), id);
        return worker;
    }

    private Object createTraining(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long org = user.organization();
        return repository.insertTraining(
                org, text(input, "title"), time(input, "startsAt"), time(input, "endsAt"));
    }

    private Object enrollTraining(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long org = user.organization();
        long training = number(input, "trainingId"), worker = number(input, "caregiverId");
        scope.organization(user, repository.findTrainingOrganization(training));
        scope.worker(user, worker);
        return repository.enrollTraining(training, worker);
    }

    private Object recordTrainingResult(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "MANAGER");
        long org = user.organization();
        long record = number(input, "recordId");
        var tr = repository.findTrainingRecordForUpdate(record);
        scope.organization(user, id(tr, "organization_id"));
        if (!tr.get("result").equals("PENDING")) {
            throw new Rule("已登记成绩不能覆盖");
        }
        if (repository.countFinishedTraining((Number) tr.get("training_id")) == 0) {
            throw new Rule("培训尚未结束");
        }
        long score = number(input, "score");
        return repository.recordTrainingResult(
                score, score >= 60 ? "PASS" : "FAIL", user.id(), record);
    }
}
