package edu.bjfu.eldercare.infrastructure.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bjfu.eldercare.application.port.Crypto;
import edu.bjfu.eldercare.domain.shared.Rule;
import edu.bjfu.eldercare.infrastructure.persistence.Store;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, transactional enrichment of the course seed database; never runs on normal startup. */
@Component
@ConditionalOnProperty(name = "app.demo-content", havingValue = "true")
public class DemoContent implements CommandLineRunner {
    private final Store d;
    private final Crypto crypto;
    private final JsonNode people;

    public DemoContent(Store d, Crypto crypto, ObjectMapper mapper) throws IOException {
        this.d = d;
        this.crypto = crypto;
        try (var input = new ClassPathResource("static/demo-people.json").getInputStream()) {
            people = mapper.readTree(input);
        }
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Serializes concurrent imports without changing the database schema.
        d.one("SELECT id FROM community WHERE id=1 AND area_code='DEMO-1' FOR UPDATE");
        var family =
                d.one(
                        "SELECT password_hash FROM account WHERE id=2 AND username='family1' AND"
                                + " role='FAMILY' AND community_id=1");
        d.one(
                "SELECT id FROM account WHERE id=1 AND username='admin1' AND role='ADMIN' AND"
                        + " community_id=1");
        d.one("SELECT id FROM caregiver WHERE id=1 AND account_id=3 AND organization_id=1");
        d.one("SELECT id FROM service_item WHERE id=1");
        try {
            d.update("SET @actor='seed:demo-content'");
            renameSeedPeople();
            Map<String, Long> bundles = new HashMap<>();
            int index = 0;
            LocalDateTime now = LocalDateTime.now().withNano(0);
            for (var person : people.path("additionalElders")) {
                addElder(person, index++, now, family.get("password_hash").toString(), bundles);
            }
        } finally {
            d.update("SET @actor=NULL");
        }
    }

    private void renameSeedPeople() {
        for (var person : people.path("elders")) {
            long id = person.path("id").asLong();
            if (d.count("SELECT COUNT(*) FROM elder WHERE id=?", id) == 0) {
                continue;
            }
            String identity = crypto.hash("SYNTHETIC-ID-" + id);
            if (d.count("SELECT COUNT(*) FROM elder WHERE id=? AND identity_hash=?", id, identity)
                    != 1) {
                throw new Rule("演示档案身份不匹配，停止导入");
            }
            d.update(
                    "UPDATE elder SET name=? WHERE id=? AND name=?",
                    person.path("name").asText(),
                    id,
                    "演示老人" + id);
        }
        for (var person : people.path("caregivers")) {
            long id = person.path("id").asLong();
            String name = person.path("name").asText();
            int updated =
                    d.update(
                            "UPDATE caregiver c JOIN account a ON a.id=c.account_id SET c.name=?"
                                    + " WHERE c.id=? AND c.name=? AND a.username=? AND"
                                    + " a.role='CAREGIVER'",
                            name,
                            id,
                            "演示护工" + id,
                            "caregiver" + id);
            if (updated > 0) {
                d.update(
                        "UPDATE recruitment SET candidate_name=? WHERE caregiver_id=? AND"
                                + " candidate_name=?",
                        name,
                        id,
                        "演示护工" + id);
            }
        }
    }

    private void addElder(
            JsonNode person,
            int index,
            LocalDateTime now,
            String passwordHash,
            Map<String, Long> bundles) {
        long id = person.path("id").asLong();
        String identity = "COURSE-NEIGHBOR-" + id;
        var existing = d.rows("SELECT identity_hash FROM elder WHERE id=?", id);
        if (!existing.isEmpty()) {
            if (!crypto.hash(identity).equals(existing.get(0).get("identity_hash"))) {
                throw new Rule("演示档案编号已被其他记录使用，停止导入");
            }
            // Existing course records may have been edited through the application. Preserve them.
            return;
        }
        String level = person.path("level").asText();
        String address = person.path("address").asText();
        int x = 10000 + (index % 4) * 120, y = (index / 4) * 100;
        d.update(
                "INSERT INTO"
                    + " elder(id,name,identity_hash,identity_cipher,birth_date,address,community_id,position_x,position_y)"
                    + " VALUES(?,?,?,?,?,?,1,?,?)",
                id,
                person.path("name").asText(),
                crypto.hash(identity),
                crypto.encrypt(identity),
                LocalDate.parse(person.path("birthDate").asText()),
                address,
                x,
                y);
        for (var tag : person.path("tags")) {
            d.update("INSERT INTO elder_tag(elder_id,tag) VALUES(?,?)", id, tag.asText());
        }
        long family =
                d.insert(
                        "INSERT INTO account(username,password_hash,role,community_id)"
                                + " VALUES(?,?,'FAMILY',1)",
                        "neighbor_family" + (index + 1),
                        passwordHash);
        d.update(
                "INSERT INTO guardian(elder_id,account_id,relation_name,phone_cipher)"
                        + " VALUES(?,?,'子女',?)",
                id,
                family,
                crypto.encrypt("COURSE-PHONE-" + id));
        long assessment =
                d.insert(
                        "INSERT INTO"
                            + " assessment(elder_id,scale_name,score,ability_level,assessor_id,assessed_at,valid_until)"
                            + " VALUES(?,'老人能力评估记录表',?,?,1,?,?)",
                        id,
                        switch (level) {
                            case "SELF" -> 90;
                            case "PARTIAL" -> 60;
                            default -> 30;
                        },
                        level,
                        now.minusDays(7),
                        now.plusDays(365));
        long bundle = bundles.computeIfAbsent(level, key -> createBundle(key));
        long plan =
                d.insert(
                        "INSERT INTO"
                            + " care_plan(assessment_id,bundle_id,caregiver_id,starts_at,ends_at)"
                            + " VALUES(?,?,1,?,?)",
                        assessment,
                        bundle,
                        now.minusDays(1),
                        now.plusDays(90));
        d.update("INSERT INTO plan_item(plan_id,item_id,weekly_frequency) VALUES(?,1,2)", plan);
        LocalDateTime start =
                now.toLocalDate().plusDays(2 + index / 2).atTime(index % 2 == 0 ? 10 : 14, 0);
        d.insert(
                "INSERT INTO"
                    + " service_order(elder_id,item_id,community_id,starts_at,ends_at,address,channel,amount,created_by,position_x,position_y)"
                    + " VALUES(?,1,1,?,?,?,'COMMUNITY',(SELECT price FROM service_item WHERE"
                    + " id=1),1,?,?)",
                id,
                start,
                start.plusHours(1),
                address,
                x,
                y);
    }

    private long createBundle(String level) {
        String name =
                switch (level) {
                    case "SELF" -> "邻里助餐·自理照护";
                    case "PARTIAL" -> "邻里助餐·协助照护";
                    case "SEVERE" -> "邻里助餐·专人照护";
                    default -> throw new Rule("演示照护等级不合法");
                };
        long id = d.insert("INSERT INTO care_bundle(name,ability_level) VALUES(?,?)", name, level);
        d.update("INSERT INTO bundle_item(bundle_id,item_id,weekly_frequency) VALUES(?,1,2)", id);
        return id;
    }
}
