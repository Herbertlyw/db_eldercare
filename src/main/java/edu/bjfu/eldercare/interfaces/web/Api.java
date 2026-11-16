package edu.bjfu.eldercare.interfaces.web;

import edu.bjfu.eldercare.application.CareService;
import edu.bjfu.eldercare.application.ReadService;
import edu.bjfu.eldercare.application.port.query.ListFilter;
import edu.bjfu.eldercare.application.security.Auth;
import java.time.LocalDate;
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
        Auth.User user;
        if (action.equals("password.change") || action.equals("password.reset")) {
            boolean own = action.equals("password.change");
            Object current = body.get(own ? "oldPassword" : "operatorPassword");
            user =
                    auth.verifyPassword(
                            token, current instanceof String ? (String) current : null, own);
        } else {
            user = auth.require(token);
        }
        return Map.of("result", service.execute(user, action, body));
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
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "") String field,
            @RequestParam(required = false) Long value) {
        return reads.list(
                auth.require(token),
                kind,
                page,
                size,
                new ListFilter(search, status, from, to, field, value));
    }
}
