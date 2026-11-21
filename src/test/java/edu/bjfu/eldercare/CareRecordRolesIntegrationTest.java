package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import edu.bjfu.eldercare.application.ReadService;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import java.util.List;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.jobs=false")
class CareRecordRolesIntegrationTest {
    @Autowired ReadService reads;

    @ParameterizedTest
    @CsvSource({
        "ADMIN, true, true, true",
        "FAMILY, true, true, false",
        "CAREGIVER, true, true, false",
        "MANAGER, false, false, false",
        "STREET, false, false, false",
        "REGULATOR, false, true, true"
    })
    void careRecordsAndEmergencyQueuesFollowTheirRoleContracts(
            String role, boolean careRecords, boolean events, boolean pendingEvents) {
        Auth.User user = WorkflowIntegrationTest.u(1, role);
        for (String kind : List.of("elders", "assessments", "plans")) {
            assertAccess(careRecords, () -> reads.list(user, kind, 0, 10));
        }
        for (String kind : List.of("elders", "plans")) {
            assertAccess(careRecords, () -> reads.details(user, kind, 1));
        }
        assertAccess(events, () -> reads.list(user, "events", 0, 10));
        assertAccess(events, () -> reads.details(user, "events", 1));
        assertAccess(pendingEvents, () -> reads.list(user, "pendingEvents", 0, 10));
        assertAccess(pendingEvents, () -> reads.details(user, "pendingEvents", 1));
    }

    private static void assertAccess(boolean allowed, Executable read) {
        if (allowed) {
            assertDoesNotThrow(read);
        } else {
            assertThrows(Forbidden.class, read);
        }
    }
}
