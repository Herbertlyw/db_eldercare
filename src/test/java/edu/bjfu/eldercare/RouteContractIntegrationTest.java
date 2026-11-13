package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.ReadService;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 保留重构前的公开路由，新增或删除接口时需明确更新契约。 */
@SpringBootTest(properties = "app.jobs=false")
class RouteContractIntegrationTest {
    @Autowired CareService writes;
    @Autowired ReadService reads;
    @Autowired ObjectMapper json;

    @Test
    void allExistingActionsQueriesAndDetailsRemainRegistered() throws Exception {
        try (var in = getClass().getResourceAsStream("/api-contract.json")) {
            var contract = json.readTree(in);
            assertEquals(names(contract.get("actions")), writes.supportedActions());
            assertEquals(names(contract.get("queries")), reads.supportedQueries());
            assertEquals(names(contract.get("details")), reads.supportedDetails());
        }
    }

    private java.util.Set<String> names(com.fasterxml.jackson.databind.JsonNode array) {
        var names = new HashSet<String>();
        array.forEach(value -> names.add(value.asText()));
        return names;
    }
}
