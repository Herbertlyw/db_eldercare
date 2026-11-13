package edu.bjfu.eldercare.interfaces.web;

import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.ReadService;
import edu.bjfu.eldercare.application.security.Auth;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP 参数与身份认证入口，业务和 SQL 委托给应用服务。 */
@RestController
@RequestMapping("/api")
public class Api {

    private final Auth auth;

    private final CareService service;

    private final ReadService reads;

    public Api(Auth auth, CareService service, ReadService reads) {
        this.auth = auth;
        this.service = service;
        this.reads = reads;
    }

    @PostMapping("/login")
    public Object login(@RequestBody Map<String, String> body) {
        return auth.login(body.get("username"), body.get("password"));
    }

    @PostMapping("/logout")
    public Object logout(@RequestHeader(value = "Authorization", required = false) String token) {
        auth.logout(token);
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public Object me(@RequestHeader(value = "Authorization", required = false) String token) {
        return auth.require(token);
    }

    @GetMapping("/public/notices")
    public Object notices() {
        return reads.publicNotices();
    }

    @PostMapping("/actions/{action}")
    public Object action(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String action,
            @RequestBody Map<String, Object> body) {
        return Map.of("result", service.execute(auth.require(token), action, body));
    }

    @GetMapping("/details/{kind}/{id}")
    public Object details(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String kind,
            @PathVariable long id) {
        return reads.details(auth.require(token), kind, id);
    }

    @GetMapping("/data/{kind}")
    public Object data(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String kind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reads.list(auth.require(token), kind, page, size);
    }
}
