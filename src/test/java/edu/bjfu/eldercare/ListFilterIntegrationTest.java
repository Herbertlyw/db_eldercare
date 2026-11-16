package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.ReadService;
import edu.bjfu.eldercare.application.port.query.ListFilter;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.jobs=false")
@AutoConfigureMockMvc
@Transactional
class ListFilterIntegrationTest {
    @Autowired ReadService reads;
    @Autowired Store db;
    @Autowired CareService service;
    @Autowired Auth auth;
    @Autowired MockMvc mvc;
    Auth.User admin = WorkflowIntegrationTest.u(1, "ADMIN");

    @SuppressWarnings("unchecked")
    Map<String, Object> list(Auth.User u, String kind, int page, int size, ListFilter f) {
        return (Map<String, Object>) reads.list(u, kind, page, size, f);
    }

    ListFilter search(String text) {
        return new ListFilter(text, "", null, null, "", null);
    }

    long total(Map<String, Object> result) {
        return ((Number) result.get("total")).longValue();
    }

    @Test
    void searchFindsRecordsBeyondFirstPageAndKeepsPagination() {
        String prefix = "查找" + UUID.randomUUID();
        var center = db.one("SELECT center_x,center_y FROM community WHERE id=1");
        for (int i = 0; i < 15; i++)
            service.execute(
                    admin,
                    "elder.create",
                    Map.of(
                            "name",
                            prefix + i,
                            "identity",
                            UUID.randomUUID().toString(),
                            "birthDate",
                            "1945-01-01",
                            "address",
                            "课程地址",
                            "x",
                            center.get("center_x"),
                            "y",
                            center.get("center_y")));
        var all = list(admin, "elders", 0, 10, search(prefix));
        assertEquals(15, total(all));
        assertEquals(10, ((List<?>) all.get("rows")).size());
        assertEquals(
                5, ((List<?>) list(admin, "elders", 1, 10, search(prefix)).get("rows")).size());
        assertEquals(1, total(list(admin, "elders", 0, 10, search(prefix + "0"))));
    }

    @Test
    void keywordAndRelationshipFiltersCannotExpandRoleScope() {
        long foreign =
                ((Number) db.one("SELECT id FROM elder WHERE community_id=2 LIMIT 1").get("id"))
                        .longValue();
        db.update("UPDATE elder SET name='跨社区专用查找' WHERE id=?", foreign);
        for (String role : List.of("ADMIN", "FAMILY", "CAREGIVER")) {
            var u = WorkflowIntegrationTest.u(1, role);
            assertEquals(0, total(list(u, "elders", 0, 10, search("跨社区专用查找"))));
            assertEquals(
                    0,
                    total(
                            list(
                                    u,
                                    "elders",
                                    0,
                                    10,
                                    new ListFilter("", "", null, null, "id", foreign))));
        }
        assertThrows(
                Forbidden.class,
                () -> list(WorkflowIntegrationTest.u(1, "MANAGER"), "accounts", 0, 10, search("")));
    }

    @Test
    void searchTreatsWildcardsAndQuotesAsLiteralText() {
        db.update("UPDATE elder SET name=? WHERE id=1", "测试%_字符");
        assertEquals(1, total(list(admin, "elders", 0, 10, search("%_"))));
        assertEquals(0, total(list(admin, "elders", 0, 10, search("' OR 1=1 --"))));
    }

    @Test
    void dateAndStatusFiltersApplyBeforeCountingAndPaging() {
        var sample =
                db.one("SELECT starts_at,status FROM service_order WHERE community_id=1 LIMIT 1");
        LocalDate day = ((java.time.LocalDateTime) sample.get("starts_at")).toLocalDate();
        String state = (String) sample.get("status");
        long expected =
                db.count(
                        "SELECT COUNT(*) FROM service_order WHERE community_id=1 AND status=? AND"
                                + " starts_at>=? AND starts_at<?",
                        state,
                        day.atStartOfDay(),
                        day.plusDays(1).atStartOfDay());
        var result = list(admin, "orders", 0, 1, new ListFilter("", state, day, day, "", null));
        assertEquals(expected, total(result));
        assertEquals("starts_at", result.get("dateField"));
        assertEquals(
                0,
                total(
                        list(
                                admin,
                                "orders",
                                0,
                                10,
                                new ListFilter(
                                        "",
                                        state,
                                        day.plusDays(1000),
                                        day.plusDays(1000),
                                        "",
                                        null))));
    }

    @Test
    void filterMetadataRespectsAuthorizedRows() {
        db.update("UPDATE elder SET status='ACTIVE' WHERE community_id=1");
        db.update("UPDATE elder SET status='INACTIVE' WHERE community_id=2");
        assertEquals(
                List.of("ACTIVE"), list(admin, "elders", 0, 10, ListFilter.NONE).get("states"));
    }

    @Test
    void invalidFiltersRejectUnknownIdentifiersAndUnsupportedFields() {
        assertThrows(Rule.class, () -> new ListFilter("", "", null, null, "id OR 1=1", 1L));
        assertThrows(
                Rule.class,
                () ->
                        new ListFilter(
                                "",
                                "",
                                LocalDate.of(2026, 2, 2),
                                LocalDate.of(2026, 2, 1),
                                "",
                                null));
        assertThrows(
                Rule.class,
                () ->
                        list(
                                admin,
                                "catalog",
                                0,
                                10,
                                new ListFilter("", "ACTIVE", null, null, "", null)));
        assertThrows(
                Rule.class,
                () ->
                        list(
                                admin,
                                "catalog",
                                0,
                                10,
                                new ListFilter("", "", null, null, "elder_id", 1L)));
    }

    @Test
    void filteredHttpRequestsRequireAuthenticationAndValidateInput() throws Exception {
        mvc.perform(get("/api/data/elders").param("search", "测试"))
                .andExpect(status().isUnauthorized());
        String token = (String) auth.login("admin1", System.getenv("DEMO_PASSWORD")).get("token");
        mvc.perform(
                        get("/api/data/elders")
                                .header("Authorization", "Bearer " + token)
                                .param("search", "测试"))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/data/elders")
                                .header("Authorization", "Bearer " + token)
                                .param("field", "id OR 1=1")
                                .param("value", "1"))
                .andExpect(status().isBadRequest());
    }
}
