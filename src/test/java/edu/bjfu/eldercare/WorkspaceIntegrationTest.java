package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.*;

import edu.bjfu.eldercare.application.ReadService;
import edu.bjfu.eldercare.application.port.query.ListFilter;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "app.jobs=false")
@Transactional
class WorkspaceIntegrationTest {
    @Autowired ReadService reads;
    @Autowired Store db;

    @SuppressWarnings("unchecked")
    Map<String, Object> page(String role, String kind, int page, int size, ListFilter filter) {
        return (Map<String, Object>)
                reads.list(WorkflowIntegrationTest.u(1, role), kind, page, size, filter);
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> rows(String role, String kind, ListFilter filter) {
        return (List<Map<String, Object>>) page(role, kind, 0, 100, filter).get("rows");
    }

    ListFilter elder(long id) {
        return new ListFilter("", "", null, null, "elder_id", id);
    }

    BigDecimal number(Object n) {
        return new BigDecimal(n.toString());
    }

    @Test
    void familyMoneyUsesAllCompletedOrdersAndPaidAllocationsForThatElder() {
        var actual = rows("FAMILY", "workspaceFamily", ListFilter.NONE);
        assertFalse(actual.isEmpty());
        for (var r : actual) {
            var expected =
                    db.one(
                            "SELECT COALESCE(SUM(amount),0) amount FROM service_order WHERE"
                                    + " elder_id=? AND status='COMPLETED'",
                            r.get("elder_id"));
            var paid =
                    db.one(
                            "SELECT COALESCE(SUM(ci.amount),0) amount FROM claim_item ci JOIN claim"
                                + " c ON c.id=ci.claim_id JOIN service_order o ON o.id=ci.order_id"
                                + " WHERE o.elder_id=? AND o.status='COMPLETED' AND"
                                + " c.status='PAID'",
                            r.get("elder_id"));
            assertEquals(
                    0, number(expected.get("amount")).compareTo(number(r.get("service_amount"))));
            assertEquals(0, number(paid.get("amount")).compareTo(number(r.get("paid_subsidy"))));
        }
        assertTrue(rows("FAMILY", "workspaceFamily", elder(2)).isEmpty());
        for (String kind : List.of("selfPay", "complaints")) {
            assertTrue(rows("FAMILY", kind, elder(2)).isEmpty());
            assertTrue(
                    rows("FAMILY", kind, elder(1)).stream()
                            .allMatch(r -> ((Number) r.get("elder_id")).longValue() == 1));
        }
        db.update(
                "UPDATE guardian SET active=0 WHERE account_id=?",
                WorkflowIntegrationTest.u(1, "FAMILY").id());
        assertTrue(rows("FAMILY", "workspaceFamily", ListFilter.NONE).isEmpty());
        assertTrue(rows("FAMILY", "workspaceSchedule", ListFilter.NONE).isEmpty());
    }

    @Test
    void workspaceReadersEnforceRoleCommunityAndInstitutionBoundaries() {
        for (String role : List.of("FAMILY", "CAREGIVER", "ADMIN")) {
            assertTrue(rows(role, "workspaceSchedule", elder(2)).isEmpty());
            assertTrue(rows(role, "workspaceUpcoming", elder(2)).isEmpty());
            assertTrue(rows(role, "workspaceCarryover", elder(2)).isEmpty());
            assertTrue(rows(role, "workspaceEvents", elder(2)).isEmpty());
        }
        assertTrue(
                rows(
                                "MANAGER",
                                "workspaceShifts",
                                new ListFilter("", "", null, null, "caregiver_id", 2L))
                        .isEmpty());
        for (String kind :
                List.of(
                        "workspaceFamily",
                        "workspaceSchedule",
                        "workspaceShifts",
                        "workspaceHours",
                        "workspaceEvents"))
            assertThrows(
                    Forbidden.class,
                    () -> reads.list(WorkflowIntegrationTest.u(1, "STREET"), kind, 0, 10));
    }

    @Test
    void scheduleSortIsAppliedBeforePaginationAndDateBoundariesAreExclusive() {
        var expected =
                db.rows("SELECT id FROM service_order WHERE community_id=1 ORDER BY starts_at,id");
        assertFalse(expected.isEmpty());
        for (int i = 0; i < expected.size(); i++) {
            @SuppressWarnings("unchecked")
            var actual =
                    (List<Map<String, Object>>)
                            page("ADMIN", "workspaceSchedule", i, 1, ListFilter.NONE).get("rows");
            assertEquals(expected.get(i).get("id"), actual.get(0).get("id"));
        }
        var date = LocalDate.now().plusDays(1);
        var rows = rows("ADMIN", "workspaceSchedule", new ListFilter("", "", date, date, "", null));
        long expectedCount =
                db.count(
                        "SELECT COUNT(*) FROM service_order WHERE community_id=1 AND starts_at>=?"
                                + " AND starts_at<?",
                        date.atStartOfDay(),
                        date.plusDays(1).atStartOfDay());
        assertEquals(expectedCount, rows.size());
    }

    @Test
    void overnightShiftAppearsOnBothDaysButNotAtItsExclusiveEnd() {
        long id =
                db.insert(
                        "INSERT INTO shift(caregiver_id,starts_at,ends_at,capacity)"
                                + " VALUES(1,'2031-01-01 23:00:00','2031-01-02 02:00:00',2)");
        for (String date : List.of("2031-01-01", "2031-01-02")) {
            var day = LocalDate.parse(date);
            var matches =
                    rows(
                            "CAREGIVER",
                            "workspaceShifts",
                            new ListFilter("", "", day, day, "", null));
            assertTrue(matches.stream().anyMatch(r -> ((Number) r.get("id")).longValue() == id));
            assertTrue(matches.stream().noneMatch(r -> r.containsKey("overlap_ends_at")));
        }
        var next = LocalDate.parse("2031-01-03");
        assertTrue(
                rows("CAREGIVER", "workspaceShifts", new ListFilter("", "", next, next, "", null))
                        .isEmpty());
    }

    @Test
    void jurisdictionSummaryUsesAllAuthorizedCommunitiesAndWeightedRates() {
        var stats = rows("REGULATOR", "jurisdictionStats", ListFilter.NONE);
        var aggregate = rows("REGULATOR", "jurisdictionSummary", ListFilter.NONE).get(0);
        assertEquals(stats.size(), ((Number) aggregate.get("community_count")).intValue());
        for (String field :
                List.of(
                        "active_elders",
                        "served_elders",
                        "response_denominator",
                        "timely_responses")) {
            BigDecimal sum =
                    stats.stream()
                            .map(r -> number(r.get(field)))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(0, sum.compareTo(number(aggregate.get(field))), field);
        }
        assertThrows(
                Forbidden.class,
                () ->
                        reads.list(
                                WorkflowIntegrationTest.u(1, "FAMILY"),
                                "jurisdictionSummary",
                                0,
                                1));
    }

    @Test
    void profileIsAlwaysTheSignedInAccountAndHoursAreCurrentMonthOnly() {
        for (String role :
                List.of("ADMIN", "FAMILY", "CAREGIVER", "MANAGER", "STREET", "REGULATOR")) {
            var row = rows(role, "workspaceProfile", ListFilter.NONE).get(0);
            assertEquals(
                    WorkflowIntegrationTest.u(1, role).id(), ((Number) row.get("id")).longValue());
            assertNotNull(row.get("community_name"));
        }
        var actual = rows("CAREGIVER", "workspaceHours", ListFilter.NONE).get(0);
        long expected =
                db.count(
                        "SELECT COALESCE(SUM(h.effective_minutes),0) FROM attendance h JOIN"
                            + " v_dispatch_context d ON d.id=h.dispatch_id JOIN caregiver c ON"
                            + " c.id=d.caregiver_id WHERE c.account_id=? AND h.status='VALID' AND"
                            + " DATE_FORMAT(h.check_in,'%Y-%m')=DATE_FORMAT(CURRENT_DATE,'%Y-%m')",
                        WorkflowIntegrationTest.u(1, "CAREGIVER").id());
        assertEquals(expected, ((Number) actual.get("effective_minutes")).longValue());
    }
}
