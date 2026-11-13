package edu.bjfu.eldercare.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.AuditContext;
import edu.bjfu.eldercare.application.port.QueryExecutor;
import edu.bjfu.eldercare.application.port.RecordAccess;
import edu.bjfu.eldercare.application.port.query.QueryModule;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingTest {
    private final Auth.User user = new Auth.User(1, "admin", "ADMIN", 1, null);

    @Test
    void duplicateActionsFailAtStartupRatherThanShadowing() {
        ActionModule first = () -> Map.of("order.create", (u, input) -> 1);
        ActionModule second = () -> Map.of("order.create", (u, input) -> 2);
        assertThrows(
                IllegalStateException.class,
                () ->
                        new CareService(
                                mock(AuditContext.class),
                                mock(RecordAccess.class),
                                List.of(first, second)));
    }

    @Test
    void unknownActionCannotTouchPersistence() {
        var audit = mock(AuditContext.class);
        var access = mock(RecordAccess.class);
        var service = new CareService(audit, access, List.of());
        assertThrows(Rule.class, () -> service.execute(user, "absent", Map.of()));
        verifyNoInteractions(audit, access);
    }

    @Test
    void invalidPaginationAndUnknownRoutesCannotExecuteQueries() {
        var executor = mock(QueryExecutor.class);
        var service = new ReadService(executor, List.of());
        assertThrows(Rule.class, () -> service.list(user, "elders", -1, 20));
        assertThrows(Rule.class, () -> service.list(user, "elders", 0, 101));
        assertThrows(Rule.class, () -> service.list(user, "absent", 0, 20));
        assertThrows(Rule.class, () -> service.details(user, "absent", 1));
        verifyNoInteractions(executor);
    }

    @Test
    void duplicateQueryNamesAreRejected() {
        QueryModule module =
                new QueryModule() {
                    public Map<String, edu.bjfu.eldercare.application.port.query.QueryHandler>
                            queries() {
                        return Map.of("elders", u -> null);
                    }
                };
        assertThrows(
                IllegalStateException.class,
                () -> new ReadService(mock(QueryExecutor.class), List.of(module, module)));
    }
}
