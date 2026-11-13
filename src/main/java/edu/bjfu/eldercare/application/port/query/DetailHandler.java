package edu.bjfu.eldercare.application.port.query;

import edu.bjfu.eldercare.application.security.Auth;

@FunctionalInterface
public interface DetailHandler {

    Object read(Auth.User user, long id);
}
