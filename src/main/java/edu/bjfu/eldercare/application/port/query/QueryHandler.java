package edu.bjfu.eldercare.application.port.query;

import edu.bjfu.eldercare.application.security.Auth;

@FunctionalInterface
public interface QueryHandler {

    PageQuery prepare(Auth.User user);
}
