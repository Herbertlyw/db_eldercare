package edu.bjfu.eldercare.application.port;

import edu.bjfu.eldercare.application.security.Auth;
import java.util.Map;

/** 一个业务操作。由 CareService 在统一事务内调用。 */
@FunctionalInterface
public interface ActionHandler {

    Object handle(Auth.User user, Map<String, Object> input);
}
