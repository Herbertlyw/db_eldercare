package edu.bjfu.eldercare;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 守住源码依赖方向；行为正确性由领域测试和真实 MySQL 测试分别验证。 */
class ArchitectureTest {
    private final Path root = Path.of("src/main/java/edu/bjfu/eldercare");

    private void forbid(String folder, List<String> dependencies) throws Exception {
        try (var files = Files.walk(root.resolve(folder))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String dependency : dependencies) {
                    assertFalse(source.contains(dependency), file + " depends on " + dependency);
                }
            }
        }
    }

    @Test
    void domainRemainsIndependentOfFrameworkAndAdapters() throws Exception {
        forbid(
                "domain",
                List.of(
                        "org.springframework.",
                        "java.sql.",
                        ".application.",
                        ".infrastructure.",
                        ".interfaces."));
    }

    @Test
    void applicationCannotReachJdbcOrConcreteAdapters() throws Exception {
        forbid(
                "application",
                List.of(
                        "java.sql.",
                        "org.springframework.jdbc.",
                        ".infrastructure.",
                        ".interfaces.",
                        "SELECT ",
                        "INSERT INTO ",
                        "UPDATE ",
                        "DELETE FROM "));
    }

    @Test
    void webHasNoPersistenceDependency() throws Exception {
        forbid(
                "interfaces",
                List.of(
                        "java.sql.",
                        "org.springframework.jdbc.",
                        ".infrastructure.",
                        ".domain.repository."));
    }

    @Test
    void businessModulesDoNotDependOnOneAnother() throws Exception {
        forbid("application/service", List.of("import edu.bjfu.eldercare.application.service."));
    }
}
