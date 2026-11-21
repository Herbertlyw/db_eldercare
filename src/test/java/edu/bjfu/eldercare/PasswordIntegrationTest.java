package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Unauthorized;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "app.jobs=false")
@AutoConfigureMockMvc
class PasswordIntegrationTest {
    @Autowired Store db;
    @Autowired Auth auth;
    @Autowired CareService service;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PlatformTransactionManager tm;
    static final String INITIAL = "Course-original-123",
            NEXT = "Course-changed-456",
            TEMP = "Course-temporary-789";

    Auth.User account(String role, long community, long org) {
        String name = "pwd-" + UUID.randomUUID();
        long id =
                db.insert(
                        "INSERT INTO"
                            + " account(username,password_hash,role,community_id,organization_id)"
                            + " VALUES(?,?,?,?,?)",
                        name,
                        auth.password(INITIAL),
                        role,
                        community,
                        org);
        return new Auth.User(id, name, role, community, org);
    }

    String token(Auth.User user, String password) {
        return (String) auth.login(user.name(), password).get("token");
    }

    Map<String, Object> change(String old, String next, String confirm) {
        return Map.of("oldPassword", old, "newPassword", next, "confirmPassword", confirm);
    }

    Map<String, Object> reset(Auth.User target) {
        return Map.of(
                "username",
                target.name(),
                "operatorPassword",
                INITIAL,
                "newPassword",
                TEMP,
                "confirmPassword",
                TEMP,
                "reason",
                "课程测试：核实身份后重置");
    }

    int post(String token, String action, Map<String, Object> data) throws Exception {
        return mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                        "/api/actions/" + action)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(data)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    Map<String, Object> state(Auth.User u) {
        return db.one(
                "SELECT password_hash,password_version,must_change_password,failures FROM account"
                        + " WHERE id=?",
                u.id());
    }

    @Test
    void caseInsensitiveLoginKeepsTheAccountIdentityOnTheFamilyHomePage() throws Exception {
        var family = account("FAMILY", 1, 1);
        var login = auth.login(family.name().toUpperCase(Locale.ROOT), INITIAL);
        String authorization = "Bearer " + login.get("token");

        var me =
                mvc.perform(get("/api/me").header("Authorization", authorization))
                        .andReturn()
                        .getResponse();
        assertEquals(200, me.getStatus());
        assertEquals(family, json.readValue(me.getContentAsByteArray(), Auth.User.class));
        assertEquals(family, login.get("user"));

        var profile =
                mvc.perform(
                                get("/api/data/workspaceProfile")
                                        .header("Authorization", authorization))
                        .andReturn()
                        .getResponse();
        assertEquals(200, profile.getStatus());
        assertEquals(
                family.name(),
                json.readTree(profile.getContentAsByteArray()).at("/rows/0/display_name").asText());
    }

    @Test
    void selfChangeRevokesAllSessionsAndDoesNotRecordSecrets() throws Exception {
        var u = account("FAMILY", 1, 1);
        String first = token(u, INITIAL), second = token(u, INITIAL);
        assertEquals(200, post(first, "password.change", change(INITIAL, NEXT, NEXT)));
        assertThrows(Unauthorized.class, () -> auth.require("Bearer " + first));
        assertThrows(Unauthorized.class, () -> auth.require("Bearer " + second));
        assertThrows(RuntimeException.class, () -> auth.login(u.name(), INITIAL));
        assertEquals(u, auth.require("Bearer " + token(u, NEXT)));
        assertNotEquals(NEXT, state(u).get("password_hash"));
        String audit =
                db.one(
                                "SELECT operation,after_data FROM audit_log WHERE"
                                    + " table_name='account' AND row_id=? ORDER BY id DESC LIMIT 1",
                                u.id())
                        .toString();
        assertTrue(audit.contains("PASSWORD_CHANGE"));
        assertFalse(audit.contains(INITIAL));
        assertFalse(audit.contains(NEXT));
    }

    @Test
    void badPasswordInputsLeaveCredentialsUnchanged() throws Exception {
        var u = account("FAMILY", 1, 1);
        String session = token(u, INITIAL);
        Object hash = state(u).get("password_hash");
        for (var input :
                List.of(
                        change("wrong", NEXT, NEXT),
                        change(INITIAL, NEXT, "different"),
                        change(INITIAL, "short", "short"),
                        change(INITIAL, INITIAL, INITIAL),
                        change(INITIAL, "中".repeat(25), "中".repeat(25)))) {
            assertEquals(400, post(session, "password.change", input));
            assertEquals(hash, state(u).get("password_hash"));
            assertEquals(0L, ((Number) state(u).get("password_version")).longValue());
        }
    }

    @Test
    void administratorResetForcesChangeBeforeAnyProtectedBusiness() throws Exception {
        var admin = account("ADMIN", 1, 1);
        var family = account("FAMILY", 1, 1);
        String old = token(family, INITIAL);
        db.update(
                "UPDATE account SET failures=5,locked_until=NOW()+INTERVAL 15 MINUTE WHERE id=?",
                family.id());
        assertEquals(200, post(token(admin, INITIAL), "password.reset", reset(family)));
        assertThrows(Unauthorized.class, () -> auth.require("Bearer " + old));
        var login = auth.login(family.name(), TEMP);
        assertEquals(true, login.get("mustChangePassword"));
        String limited = (String) login.get("token");
        for (String url : List.of("/api/me", "/api/data/elders", "/api/details/elders/1"))
            mvc.perform(get(url).header("Authorization", "Bearer " + limited))
                    .andExpect(status().isForbidden());
        assertEquals(
                403,
                post(
                        limited,
                        "event.create",
                        Map.of("elderId", 1, "level", 1, "description", "blocked")));
        assertEquals(200, post(limited, "password.change", change(TEMP, NEXT, NEXT)));
        assertThrows(Unauthorized.class, () -> auth.require("Bearer " + limited));
        assertEquals(false, auth.login(family.name(), NEXT).get("mustChangePassword"));
    }

    @Test
    void resetCannotCrossCommunityOrReachPrivilegedAccounts() throws Exception {
        var admin = account("ADMIN", 1, 1);
        String session = token(admin, INITIAL);
        for (var target :
                List.of(
                        account("FAMILY", 2, 2),
                        account("ADMIN", 1, 1),
                        account("MANAGER", 1, 1),
                        account("STREET", 1, 1),
                        account("REGULATOR", 1, 1),
                        admin)) {
            Object hash = state(target).get("password_hash");
            assertEquals(403, post(session, "password.reset", reset(target)));
            assertEquals(hash, state(target).get("password_hash"));
        }
        var inactive = account("FAMILY", 1, 1);
        db.update("UPDATE account SET active=0 WHERE id=?", inactive.id());
        assertEquals(403, post(session, "password.reset", reset(inactive)));
    }

    @Test
    void managerCanOnlyResetOwnOrganizationCaregivers() throws Exception {
        var manager = account("MANAGER", 1, 1);
        String session = token(manager, INITIAL);
        assertEquals(200, post(session, "password.reset", reset(account("CAREGIVER", 1, 1))));
        assertEquals(403, post(session, "password.reset", reset(account("CAREGIVER", 2, 2))));
        assertEquals(403, post(session, "password.reset", reset(account("FAMILY", 1, 1))));
        var ordinary = account("FAMILY", 1, 1);
        assertEquals(
                403,
                post(token(ordinary, INITIAL), "password.reset", reset(account("FAMILY", 1, 1))));
    }

    @Test
    void repeatedWrongCurrentPasswordIsRateLimitedAndResetRequiresOperatorPassword()
            throws Exception {
        var admin = account("ADMIN", 1, 1);
        var target = account("FAMILY", 1, 1);
        String session = token(admin, INITIAL);
        var input = new HashMap<>(reset(target));
        input.put("operatorPassword", "wrong");
        for (int i = 0; i < 5; i++) assertEquals(400, post(session, "password.reset", input));
        assertEquals(5, ((Number) state(admin).get("failures")).intValue());
        assertEquals(400, post(session, "password.reset", reset(target)));
        assertEquals(0L, ((Number) state(target).get("password_version")).longValue());
    }

    @Test
    void maintenanceRecoveryCannotBeInvokedThroughWebRoles() throws Exception {
        for (String role :
                List.of("ADMIN", "FAMILY", "CAREGIVER", "MANAGER", "STREET", "REGULATOR")) {
            var u = account(role, 1, 1);
            assertEquals(403, post(token(u, INITIAL), "password.recover", reset(u)));
        }
        var staff = account("REGULATOR", 1, 1);
        String old = token(staff, INITIAL);
        service.execute(
                new Auth.User(0, "local-maintainer", "MAINTENANCE", 0, null),
                "password.recover",
                reset(staff));
        assertThrows(Unauthorized.class, () -> auth.require("Bearer " + old));
        assertEquals(true, auth.login(staff.name(), TEMP).get("mustChangePassword"));
    }

    @Test
    void rollbackPreservesPasswordVersionAndAudit() {
        var u = account("FAMILY", 1, 1);
        var before = state(u);
        long logs =
                db.count(
                        "SELECT COUNT(*) FROM audit_log WHERE table_name='account' AND row_id=?",
                        u.id());
        new TransactionTemplate(tm)
                .execute(
                        tx -> {
                            service.execute(u, "password.change", change(INITIAL, NEXT, NEXT));
                            tx.setRollbackOnly();
                            return null;
                        });
        assertEquals(before, state(u));
        assertEquals(
                logs,
                db.count(
                        "SELECT COUNT(*) FROM audit_log WHERE table_name='account' AND row_id=?",
                        u.id()));
    }

    @Test
    void concurrentPasswordChangesCannotOverwriteEachOther() throws Exception {
        var u = account("FAMILY", 1, 1);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 2; i++)
                attempts.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        service.execute(
                                                u, "password.change", change(INITIAL, NEXT, NEXT));
                                        return true;
                                    } catch (RuntimeException e) {
                                        return false;
                                    }
                                }));
            start.countDown();
            int success = 0;
            for (var attempt : attempts) if (attempt.get(10, TimeUnit.SECONDS)) success++;
            assertEquals(1, success);
            assertEquals(1L, ((Number) state(u).get("password_version")).longValue());
        } finally {
            pool.shutdownNow();
        }
    }
}
