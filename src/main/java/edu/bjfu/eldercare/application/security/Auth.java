package edu.bjfu.eldercare.application.security;

import edu.bjfu.eldercare.application.port.IdentityRepository;
import edu.bjfu.eldercare.application.port.PasswordHasher;
import edu.bjfu.eldercare.application.support.Rows;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class Auth {

    public record User(long id, String name, String role, long community, Long organization) {}

    private record Session(User user, Instant expires, long passwordVersion, boolean mustChange) {}

    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

    private final IdentityRepository repository;

    private final TransactionTemplate tx;

    private final PasswordHasher encoder;

    public Auth(
            IdentityRepository repository, PasswordHasher encoder, PlatformTransactionManager tm) {
        this.repository = repository;
        this.encoder = encoder;
        tx = new TransactionTemplate(tm);
    }

    public String password(String raw) {
        return encoder.encode(raw);
    }

    public Map<String, Object> login(String username, String password) {
        if (username == null
                || password == null
                || username.length() > 50
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new Rule("用户名或密码错误");
        }
        Session signedIn =
                tx.execute(
                        status -> {
                            var rs = repository.findAccountForUpdate(username);
                            if (rs.isEmpty()) {
                                encoder.matches(
                                        password,
                                        "$2a$12$vJeA1taUYd6WjjfayB8Ue.6SAnx2b6luEaoPPkAzcKc4F6DbsHvRS");
                                return null;
                            }
                            var a = rs.get(0);
                            if (!Boolean.TRUE.equals(a.get("active"))) {
                                return null;
                            }
                            Object locked = a.get("locked_until");
                            if (locked != null
                                    && ((LocalDateTime) locked).isAfter(LocalDateTime.now())) {
                                return null;
                            }
                            if (!encoder.matches(password, (String) a.get("password_hash"))) {
                                repository.recordFailure(a.get("id"));
                                return null;
                            }
                            repository.resetFailures(a.get("id"));
                            User user =
                                    new User(
                                            Rows.id(a, "id"),
                                            (String) a.get("username"),
                                            (String) a.get("role"),
                                            Rows.id(a, "community_id"),
                                            a.get("organization_id") == null
                                                    ? null
                                                    : Rows.id(a, "organization_id"));
                            return new Session(
                                    user,
                                    Instant.now().plusSeconds(1800),
                                    Rows.id(a, "password_version"),
                                    Boolean.TRUE.equals(a.get("must_change_password")));
                        });
        if (signedIn == null) {
            throw new Rule("用户名或密码错误，或账户暂时锁定");
        }
        sessions.entrySet().removeIf(e -> e.getValue().expires.isBefore(Instant.now()));
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        sessions.put(token, signedIn);
        return Map.of(
                "token",
                token,
                "user",
                signedIn.user,
                "expiresIn",
                1800,
                "mustChangePassword",
                signedIn.mustChange);
    }

    public User require(String auth) {
        return require(auth, false);
    }

    private User require(String auth, boolean allowPasswordChange) {
        if (auth == null || !auth.startsWith("Bearer ")) {
            throw new Unauthorized();
        }
        String token = auth.substring(7);
        Session session = sessions.get(token);
        if (session == null || !session.expires.isAfter(Instant.now())) {
            sessions.remove(token);
            throw new Unauthorized();
        }
        var rows = repository.findActiveAccount(session.user.id());
        if (rows.isEmpty()) {
            sessions.remove(token);
            throw new Unauthorized();
        }
        var a = rows.get(0);
        User current =
                new User(
                        Rows.id(a, "id"),
                        (String) a.get("username"),
                        (String) a.get("role"),
                        Rows.id(a, "community_id"),
                        a.get("organization_id") == null ? null : Rows.id(a, "organization_id"));
        if (!current.equals(session.user)
                || Rows.id(a, "password_version") != session.passwordVersion) {
            sessions.remove(token);
            throw new Unauthorized();
        }
        if (Boolean.TRUE.equals(a.get("must_change_password")) && !allowPasswordChange) {
            throw new PasswordChangeRequired();
        }
        return current;
    }

    /** Password verification commits its failure counter before the business transaction begins. */
    public User verifyPassword(String authorization, String raw, boolean allowPasswordChange) {
        User user = require(authorization, allowPasswordChange);
        if (raw == null || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new Rule("请填写当前密码");
        }
        Boolean matched =
                tx.execute(
                        status -> {
                            var rows = repository.findAccountForUpdate(user.name());
                            require(authorization, allowPasswordChange);
                            var account = rows.get(0);
                            Object locked = account.get("locked_until");
                            if (locked != null
                                    && ((LocalDateTime) locked).isAfter(LocalDateTime.now())) {
                                return false;
                            }
                            if (!encoder.matches(raw, (String) account.get("password_hash"))) {
                                repository.recordFailure(user.id());
                                return false;
                            }
                            repository.resetFailures(user.id());
                            return true;
                        });
        if (!Boolean.TRUE.equals(matched)) {
            throw new Rule("当前密码错误，或账户暂时锁定");
        }
        return user;
    }

    public void logout(String auth) {
        if (auth != null) {
            sessions.remove(auth.replace("Bearer ", ""));
        }
    }

    public static void role(User u, String... roles) {
        if (!Arrays.asList(roles).contains(u.role)) {
            throw new Forbidden();
        }
    }
}
