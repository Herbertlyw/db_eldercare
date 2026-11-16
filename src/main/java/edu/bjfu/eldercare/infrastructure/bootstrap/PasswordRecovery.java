package edu.bjfu.eldercare.infrastructure.bootstrap;

import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicit non-web recovery for staff accounts; password is never a command-line argument. */
@Component
@ConditionalOnProperty(name = "app.password-recovery", havingValue = "true")
public class PasswordRecovery implements CommandLineRunner {
    private final CareService service;
    private final Environment environment;

    public PasswordRecovery(CareService service, Environment environment) {
        this.service = service;
        this.environment = environment;
    }

    public void run(String... args) {
        if (!"none".equals(environment.getProperty("spring.main.web-application-type"))
                || !"false".equals(environment.getProperty("app.jobs")))
            throw new Rule("密码维护须关闭Web服务和定时任务");
        String password = environment.getProperty("RECOVERY_PASSWORD");
        String username = environment.getProperty("RECOVERY_USERNAME");
        String reason = environment.getProperty("RECOVERY_REASON");
        if (password == null || username == null || reason == null) throw new Rule("缺少密码维护环境变量");
        service.execute(
                new Auth.User(0, "local-maintainer", "MAINTENANCE", 0, null),
                "password.recover",
                Map.of(
                        "username",
                        username,
                        "newPassword",
                        password,
                        "confirmPassword",
                        password,
                        "reason",
                        reason));
        System.out.println("账号已恢复；用户登录后须修改临时密码。");
    }
}
