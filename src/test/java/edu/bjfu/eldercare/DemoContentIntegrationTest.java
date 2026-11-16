package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.port.Crypto;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.infrastructure.bootstrap.DemoContent;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"app.jobs=false"})
class DemoContentIntegrationTest {
    @Autowired Store d;
    @Autowired Crypto crypto;
    @Autowired ObjectMapper mapper;
    @Autowired CareService service;
    @Autowired PlatformTransactionManager tm;

    @Test
    void importsLinkedRecordsOnceAndPreservesEditsAndAccessBoundaries() throws IOException {
        var content = new DemoContent(d, crypto, mapper);
        new TransactionTemplate(tm)
                .execute(
                        status -> {
                            status.setRollbackOnly();
                            long elders = d.count("SELECT COUNT(*) FROM elder");
                            long orders = d.count("SELECT COUNT(*) FROM service_order");
                            content.run();
                            assertEquals(elders + 12, d.count("SELECT COUNT(*) FROM elder"));
                            assertEquals(
                                    orders + 12, d.count("SELECT COUNT(*) FROM service_order"));
                            assertEquals(
                                    12,
                                    d.count(
                                            "SELECT COUNT(*) FROM elder e JOIN guardian g ON"
                                                + " g.elder_id=e.id JOIN assessment a ON"
                                                + " a.elder_id=e.id JOIN care_plan p ON"
                                                + " p.assessment_id=a.id JOIN plan_item i ON"
                                                + " i.plan_id=p.id JOIN service_order o ON"
                                                + " o.elder_id=e.id AND o.item_id=i.item_id WHERE"
                                                + " e.id BETWEEN 1001 AND 1012"));
                            assertEquals(
                                    3,
                                    d.count(
                                            "SELECT COUNT(DISTINCT ability_level) FROM assessment"
                                                    + " WHERE elder_id BETWEEN 1001 AND 1012"));
                            d.update("UPDATE elder SET name='自定义姓名' WHERE id=1001");
                            content.run();
                            assertEquals(elders + 12, d.count("SELECT COUNT(*) FROM elder"));
                            assertEquals(
                                    orders + 12, d.count("SELECT COUNT(*) FROM service_order"));
                            assertEquals(
                                    "自定义姓名",
                                    d.one("SELECT name FROM elder WHERE id=1001").get("name"));
                            service.elderScope(new Auth.User(1, "admin1", "ADMIN", 1, 1L), 1001);
                            assertThrows(
                                    Forbidden.class,
                                    () ->
                                            service.elderScope(
                                                    new Auth.User(7, "admin2", "ADMIN", 2, 2L),
                                                    1001));
                            assertThrows(
                                    Forbidden.class,
                                    () ->
                                            service.elderScope(
                                                    new Auth.User(2, "family1", "FAMILY", 1, 1L),
                                                    1001));
                            long family =
                                    d.count("SELECT account_id FROM guardian WHERE elder_id=1001");
                            service.elderScope(
                                    new Auth.User(family, "neighbor_family1", "FAMILY", 1, null),
                                    1001);
                            return null;
                        });
    }

    @Test
    void lateIdentifierConflictRollsBackTheWholeImport() throws IOException {
        var content = new DemoContent(d, crypto, mapper);
        long elders = d.count("SELECT COUNT(*) FROM elder");
        long plans = d.count("SELECT COUNT(*) FROM care_plan");
        long accounts = d.count("SELECT COUNT(*) FROM account");
        Object name = d.one("SELECT name FROM elder WHERE id=1").get("name");
        assertThrows(
                Rule.class,
                () ->
                        new TransactionTemplate(tm)
                                .execute(
                                        status -> {
                                            d.update(
                                                    "INSERT INTO"
                                                        + " elder(id,name,identity_hash,identity_cipher,birth_date,address,community_id,position_x)"
                                                        + " VALUES(1008,'冲突测试',REPEAT('e',64),'SYNTHETIC-COLLISION','1950-01-01','测试地址',1,10000)");
                                            content.run();
                                            return null;
                                        }));
        assertEquals(elders, d.count("SELECT COUNT(*) FROM elder"));
        assertEquals(plans, d.count("SELECT COUNT(*) FROM care_plan"));
        assertEquals(accounts, d.count("SELECT COUNT(*) FROM account"));
        assertEquals(name, d.one("SELECT name FROM elder WHERE id=1").get("name"));
    }
}
