package edu.bjfu.eldercare.infrastructure.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/** Fictional names for the initial dataset. Identifiers and authorization scopes stay stable. */
final class SeedLabels {
    private final JsonNode places;
    private final JsonNode people;

    SeedLabels() {
        var mapper = new ObjectMapper();
        try (var labels = SeedLabels.class.getResourceAsStream("/seed-labels.json");
                var portraits = SeedLabels.class.getResourceAsStream("/static/demo-people.json")) {
            if (labels == null || portraits == null) {
                throw new IllegalStateException("初始化名称资料缺失");
            }
            places = mapper.readTree(labels);
            people = mapper.readTree(portraits);
        } catch (IOException error) {
            throw new IllegalStateException("初始化名称资料无法读取", error);
        }
    }

    String place(int id, String field) {
        return places.required(id - 1).required(field).asText();
    }

    String person(int id, String group) {
        for (var person : people.required(group)) {
            if (person.required("id").asInt() == id) {
                return person.required("name").asText();
            }
        }
        throw new IllegalArgumentException("初始化人员资料缺失：" + group + "/" + id);
    }
}
