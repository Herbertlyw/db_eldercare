package edu.bjfu.eldercare.application.port;

import edu.bjfu.eldercare.application.security.Auth;
import java.util.Map;

public interface Scope {

    boolean regulator(Auth.User u);

    void community(Auth.User u, long community);

    void elder(Auth.User u, long elder);

    void worker(Auth.User u, long worker);

    Map<String, Object> order(Auth.User u, long order);

    Map<String, Object> claim(Auth.User u, long claim);

    void organization(Auth.User u, long organization);

    Map<String, Object> inspection(Auth.User u, long inspection);
}
