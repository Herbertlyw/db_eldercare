package edu.bjfu.eldercare.application.security;

import static edu.bjfu.eldercare.application.support.Inputs.text;

import edu.bjfu.eldercare.domain.shared.Rule;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 新建家属账号和招聘录用共用同一密码规则。 */
@Component
public class AccountPasswords {

    private final Auth auth;

    public AccountPasswords(Auth auth) {
        this.auth = auth;
    }

    public String encode(Map<String, Object> input) {
        return encode(text(input, "password"));
    }

    public String encode(String password) {
        if (password == null
                || password.isBlank()
                || password.codePointCount(0, password.length()) < 10
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new Rule("初始密码至少 10 个字符，且不超过 72 字节");
        }
        return auth.password(password);
    }
}
