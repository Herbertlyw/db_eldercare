package edu.bjfu.eldercare.interfaces.web;

import edu.bjfu.eldercare.application.security.Forbidden;
import edu.bjfu.eldercare.application.security.Unauthorized;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class Errors {

    @ExceptionHandler(Unauthorized.class)
    ResponseEntity<?> unauth() {
        return ResponseEntity.status(401).body(Map.of("error", "请登录或重新登录"));
    }

    @ExceptionHandler(Forbidden.class)
    ResponseEntity<?> forbidden() {
        return ResponseEntity.status(403).body(Map.of("error", "无权操作该数据"));
    }

    @ExceptionHandler({Rule.class, IllegalArgumentException.class})
    ResponseEntity<?> bad(RuntimeException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage() == null ? "输入无效" : e.getMessage()));
    }

    @ExceptionHandler({
        java.time.DateTimeException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    ResponseEntity<?> malformed() {
        return ResponseEntity.badRequest().body(Map.of("error", "请求格式或日期无效"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> data(DataAccessException e) {
        String msg = e.getMostSpecificCause().getMessage();
        String safe = "数据库规则拒绝了操作，请检查关联、重复数据、状态或时间范围";
        for (String code :
                List.of(
                        "PLAN_ASSESSMENT_SUPERSEDED",
                        "TRANSFER_DEVICE_BOUND",
                        "BINDING_COMMUNITY_INVALID",
                        "HISTORICAL_FACT_IMMUTABLE",
                        "DISPATCH_STATE_INVALID",
                        "PLAN_OVERLAP",
                        "SHIFT_OVERLAP",
                        "PLAN_ASSESSMENT_INVALID",
                        "WORKER_NOT_QUALIFIED",
                        "WORKER_TIME_CONFLICT",
                        "SHIFT_FULL_OR_OUTSIDE",
                        "ORDER_PLAN_INVALID",
                        "CONFIRMATION_REQUIRED",
                        "RECTIFICATION_REQUIRED",
                        "CLAIM_ORDER_INELIGIBLE",
                        "CLAIM_EVIDENCE_MISSING",
                        "CLAIM_STATE_INVALID",
                        "HISTORY_DELETE_FORBIDDEN",
                        "VITAL_BINDING_TIME_INVALID")) {
            if (msg != null && msg.contains(code)) {
                safe = code;
            }
        }
        return ResponseEntity.status(409).body(Map.of("error", safe));
    }
}
