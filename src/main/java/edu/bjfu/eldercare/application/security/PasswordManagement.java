package edu.bjfu.eldercare.application.security;

import static edu.bjfu.eldercare.application.support.Inputs.text;
import static edu.bjfu.eldercare.application.support.Rows.id;

import edu.bjfu.eldercare.application.port.ActionHandler;
import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.IdentityRepository;
import edu.bjfu.eldercare.application.port.PasswordHasher;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Account recovery policy; all writes join CareService's transaction. */
@Component
public class PasswordManagement implements ActionModule {
    private final IdentityRepository repository;
    private final PasswordHasher hasher;
    private final AccountPasswords passwords;

    public PasswordManagement(
            IdentityRepository repository, PasswordHasher hasher, AccountPasswords passwords) {
        this.repository = repository;
        this.hasher = hasher;
        this.passwords = passwords;
    }

    public Map<String, ActionHandler> actions() {
        return Map.of(
                "password.change",
                this::change,
                "password.reset",
                this::reset,
                "password.recover",
                this::recover);
    }

    private Map<String, Object> account(String username) {
        var rows = repository.findAccountForUpdate(username);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("active"))) {
            throw new Forbidden();
        }
        return rows.get(0);
    }

    private String checkedHash(Map<String, Object> input, Map<String, Object> target) {
        Object value = input.get("newPassword");
        if (!(value instanceof String raw) || !raw.equals(input.get("confirmPassword"))) {
            throw new Rule("两次输入的新密码不一致");
        }
        String hash = passwords.encode(raw);
        if (hasher.matches(raw, (String) target.get("password_hash"))) {
            throw new Rule("新密码不能与当前密码相同");
        }
        return hash;
    }

    private void verifyActor(
            Auth.User actor, Map<String, Object> record, String raw, boolean allowForced) {
        if (id(record, "id") != actor.id()
                || !actor.role().equals(record.get("role"))
                || id(record, "community_id") != actor.community()
                || !java.util.Objects.equals(
                        actor.organization(),
                        record.get("organization_id") == null
                                ? null
                                : id(record, "organization_id"))) {
            throw new Forbidden();
        }
        if (Boolean.TRUE.equals(record.get("must_change_password")) && !allowForced)
            throw new PasswordChangeRequired();
        Object locked = record.get("locked_until");
        if (locked != null && ((LocalDateTime) locked).isAfter(LocalDateTime.now()))
            throw new Rule("账户暂时锁定，请稍后再试");
        if (raw == null || !hasher.matches(raw, (String) record.get("password_hash")))
            throw new Rule("当前密码错误，请重新登录后再试");
    }

    private Object change(Auth.User actor, Map<String, Object> input) {
        var target = account(actor.name());
        verifyActor(actor, target, (String) input.get("oldPassword"), true);
        repository.replacePassword(actor.id(), checkedHash(input, target), false);
        repository.recordPasswordAudit(
                actor.id(), "PASSWORD_CHANGE", actor.id() + ":" + actor.name(), "本人修改密码");
        return "密码已修改，请重新登录";
    }

    private Object reset(Auth.User actor, Map<String, Object> input) {
        Auth.role(actor, "ADMIN", "MANAGER");
        var operator = account(actor.name());
        verifyActor(actor, operator, (String) input.get("operatorPassword"), false);
        var target = account(text(input, "username"));
        boolean allowed =
                actor.role().equals("ADMIN")
                        ? target.get("role").equals("FAMILY")
                                && id(target, "community_id") == actor.community()
                        : target.get("role").equals("CAREGIVER")
                                && actor.organization() != null
                                && target.get("organization_id") != null
                                && id(target, "organization_id") == actor.organization();
        if (!allowed || actor.id() == id(target, "id")) throw new Forbidden();
        return replace(actor, input, target, "PASSWORD_RESET");
    }

    private Object recover(Auth.User actor, Map<String, Object> input) {
        // No database account has this role; only the explicit non-web maintenance runner uses it.
        if (!actor.role().equals("MAINTENANCE") || actor.id() != 0) throw new Forbidden();
        return replace(actor, input, account(text(input, "username")), "PASSWORD_RECOVERY");
    }

    private Object replace(
            Auth.User actor,
            Map<String, Object> input,
            Map<String, Object> target,
            String operation) {
        String reason = text(input, "reason");
        if (reason.length() > 300) throw new Rule("重置原因不超过300字");
        repository.replacePassword(id(target, "id"), checkedHash(input, target), true);
        repository.recordPasswordAudit(
                id(target, "id"), operation, actor.id() + ":" + actor.name(), reason);
        return "已设置临时密码；请线下交给本人，首次登录后须修改";
    }
}
