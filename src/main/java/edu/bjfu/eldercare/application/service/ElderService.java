package edu.bjfu.eldercare.application.service;

import static edu.bjfu.eldercare.application.support.Inputs.choice;
import static edu.bjfu.eldercare.application.support.Inputs.decimal;
import static edu.bjfu.eldercare.application.support.Inputs.flag;
import static edu.bjfu.eldercare.application.support.Inputs.number;
import static edu.bjfu.eldercare.application.support.Inputs.objects;
import static edu.bjfu.eldercare.application.support.Inputs.rawNumber;
import static edu.bjfu.eldercare.application.support.Inputs.rawText;
import static edu.bjfu.eldercare.application.support.Inputs.text;
import static edu.bjfu.eldercare.application.support.Inputs.time;
import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.ActionHandler;
import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.Crypto;
import edu.bjfu.eldercare.application.port.RecordAccess;
import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.AccountPasswords;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.domain.care.CarePlan;
import edu.bjfu.eldercare.domain.repository.ElderRepository;
import edu.bjfu.eldercare.domain.shared.Position;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 老人档案、评估、照护计划及服务目录。 */
@Component
public class ElderService implements ActionModule {

    private final ElderRepository repository;

    private final RecordAccess access;

    private final Scope scope;

    private final Crypto crypto;

    private final AccountPasswords passwords;

    public ElderService(
            RecordAccess access,
            ElderRepository repository,
            Scope scope,
            Crypto crypto,
            AccountPasswords passwords) {
        this.access = access;
        this.repository = repository;
        this.scope = scope;
        this.crypto = crypto;
        this.passwords = passwords;
    }

    public Map<String, ActionHandler> actions() {
        return Map.ofEntries(
                Map.entry("elder.create", this::createElder),
                Map.entry("guardian.create", this::createGuardian),
                Map.entry("elder.update", this::updateElder),
                Map.entry("elder.transfer", this::transferElder),
                Map.entry("elder.deactivate", this::deactivateElder),
                Map.entry("assessment.create", this::createAssessment),
                Map.entry("plan.create", this::createPlan),
                Map.entry("account.create", this::createAccount),
                Map.entry("elder.tags", this::setElderTags),
                Map.entry("guardian.update", this::updateGuardian),
                Map.entry("plan.revise", this::revisePlan),
                Map.entry("plan.state", this::changePlanState),
                Map.entry("catalog.create", this::createCatalog),
                Map.entry("bundle.create", this::createBundle));
    }

    private Object createElder(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        BigDecimal px = decimal(input, "x"), py = decimal(input, "y");
        coordinates(user.community(), px, py);
        String identity = text(input, "identity").toUpperCase(Locale.ROOT);
        return repository.insertElder(
                text(input, "name"),
                crypto.hash(identity),
                crypto.encrypt(identity),
                text(input, "birthDate"),
                text(input, "address"),
                user.community(),
                px,
                py);
    }

    private Object createGuardian(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = rawNumber(input, "elderId");
        access.elder(user, recordId);
        if (repository.countFamilyAccounts(rawNumber(input, "accountId"), user.community()) != 1) {
            throw new Rule("家属账号范围错误");
        }
        return repository.insertGuardian(
                recordId,
                rawNumber(input, "accountId"),
                rawText(input, "relation"),
                crypto.encrypt(rawText(input, "phone")));
    }

    private Object updateElder(Auth.User user, Map<String, Object> input) {
        return saveAddress(user, input, false);
    }

    private Object transferElder(Auth.User user, Map<String, Object> input) {
        return saveAddress(user, input, true);
    }

    private Object deactivateElder(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = rawNumber(input, "elderId");
        access.elder(user, recordId);
        if (repository.countUnfinishedOrders(recordId) > 0) {
            throw new Rule("请先处理未完成订单");
        }
        repository.pauseActivePlans(recordId);
        return repository.deactivateElder(recordId);
    }

    private Object createAssessment(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = rawNumber(input, "elderId");
        access.elder(user, recordId);
        return repository.insertAssessment(
                recordId,
                rawText(input, "scale"),
                rawNumber(input, "score"),
                rawText(input, "level"),
                user.id(),
                rawText(input, "validUntil"));
    }

    private Object createPlan(Auth.User user, Map<String, Object> input) {
        return plan(user, input, false);
    }

    private Object createAccount(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        return repository.insertFamilyAccount(
                text(input, "username"), passwords.encode(input), user.community());
    }

    private Object setElderTags(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "elderId");
        scope.elder(user, recordId);
        String tag = choice(input, "tag", "ALONE", "DISABLED", "LOW_INCOME");
        return flag(input, "enabled")
                ? repository.addTag(recordId, tag)
                : repository.removeTag(recordId, tag);
    }

    private Object updateGuardian(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "guardianId");
        Map<String, Object> record = repository.findGuardianForUpdate(recordId);
        scope.elder(user, id(record, "elder_id"));
        return repository.updateGuardian(
                text(input, "relation"),
                crypto.encrypt(text(input, "phone")),
                flag(input, "active"),
                recordId);
    }

    private Object revisePlan(Auth.User user, Map<String, Object> input) {
        return plan(user, input, true);
    }

    private Object changePlanState(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "planId");
        Map<String, Object> record = repository.findPlanForUpdate(recordId);
        scope.elder(user, id(record, "elder_id"));
        noPendingOrders(id(record, "elder_id"));
        String state = choice(input, "status", "ACTIVE", "PAUSED", "ENDED");
        String reason = text(input, "reason");
        new CarePlan(recordId, CarePlan.Status.valueOf((String) record.get("status")))
                .requireStateChangeable();
        int changed = repository.updatePlanStatus(state, recordId);
        repository.appendPlanStateReason(recordId, user.name(), reason);
        return changed;
    }

    private Object createCatalog(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        return repository.insertServiceItem(
                text(input, "name"),
                text(input, "category"),
                choice(input, "unit", "VISIT", "HOUR"),
                decimal(input, "price"),
                number(input, "duration"),
                flag(input, "subsidizable"));
    }

    private Object createBundle(Auth.User user, Map<String, Object> input) {
        Auth.role(user, "ADMIN");
        long bundle =
                repository.insertBundle(
                        text(input, "name"), choice(input, "level", "SELF", "PARTIAL", "SEVERE"));
        for (var item : objects(input, "items")) {
            repository.insertBundleItem(bundle, number(item, "itemId"), number(item, "frequency"));
        }
        return bundle;
    }

    private void noPendingOrders(long elder) {
        if (repository.countUnfinishedOrders(elder) > 0) {
            throw new Rule("请先处理未完成订单");
        }
    }

    private void coordinates(long community, BigDecimal x, BigDecimal y) {
        var area = repository.findCommunity(community);
        if (!new Position(x.doubleValue(), y.doubleValue())
                .within(
                        new Position(
                                ((Number) area.get("center_x")).doubleValue(),
                                ((Number) area.get("center_y")).doubleValue()),
                        ((Number) area.get("radius_m")).doubleValue())) {
            throw new Rule("地址坐标超出社区服务范围");
        }
    }

    private Object plan(Auth.User user, Map<String, Object> input, boolean revise) {
        Auth.role(user, "ADMIN");
        long elder = number(input, "elderId");
        scope.elder(user, elder);
        repository.lockElder(elder);
        scope.worker(user, number(input, "caregiverId"));
        if (repository.countAssessmentsForElder(number(input, "assessmentId"), elder) != 1) {
            throw new Rule("评估不属于此老人");
        }
        Long old = null;
        if (revise) {
            old = number(input, "planId");
            var previousPlan = repository.findPlanForUpdate(old);
            if (id(previousPlan, "elder_id") != elder) {
                throw new Rule("原计划不属于此老人");
            }
            noPendingOrders(elder);
            text(input, "reason");
            repository.endPlan(old);
        }
        long plan =
                repository.insertPlan(
                        number(input, "assessmentId"),
                        number(input, "bundleId"),
                        number(input, "caregiverId"),
                        time(input, "startsAt"),
                        time(input, "endsAt"));
        if (input.get("items") instanceof List<?> items && !items.isEmpty()) {
            for (var item : objects(input, "items")) {
                long itemId = number(item, "itemId");
                if (repository.countBundleItems(number(input, "bundleId"), itemId) == 0) {
                    throw new Rule("项目不属于所选服务包");
                }
                repository.insertPlanItem(plan, itemId, number(item, "frequency"));
            }
        } else {
            repository.copyBundleItems(plan, number(input, "bundleId"));
        }
        if (repository.countPlanItems(plan) == 0) {
            throw new Rule("照护计划必须包含服务项目");
        }
        if (revise) {
            repository.appendPlanRevision(plan, user.name(), old, text(input, "reason"));
        }
        return plan;
    }

    private Object saveAddress(Auth.User user, Map<String, Object> input, boolean transfer) {
        Auth.role(user, "ADMIN");
        long recordId = number(input, "elderId");
        scope.elder(user, recordId);
        repository.lockElder(recordId);
        noPendingOrders(recordId);
        long community = transfer ? number(input, "communityId") : user.community();
        BigDecimal px = decimal(input, "x"), py = decimal(input, "y");
        coordinates(community, px, py);
        if (community != user.community() && repository.countActiveDeviceBindings(recordId) > 0) {
            throw new Rule("请先在设备管理中解绑原社区设备，再迁移老人档案");
        }
        if (transfer) {
            repository.pauseActivePlans(recordId);
        }
        return repository.updateAddress(text(input, "address"), community, px, py, recordId);
    }
}
