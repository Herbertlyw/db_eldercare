package edu.bjfu.eldercare.application.port;

import edu.bjfu.eldercare.application.security.Auth;
import java.util.Map;

public interface RecordAccess {

    void elder(Auth.User user, long elder);

    Map<String, Object> activeOrder(Auth.User user, long orderId);
}
