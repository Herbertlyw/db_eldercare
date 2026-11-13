package edu.bjfu.eldercare.infrastructure.security;

import edu.bjfu.eldercare.application.port.Scope;
import edu.bjfu.eldercare.application.security.Auth;
import java.util.List;

/** 查询适配器使用的 SQL 权限过滤片段；业务服务只依赖 Scope 的授权操作。 */
public interface QueryScope extends Scope {
    String communityFilter(Auth.User u, String column, List<Object> args);

    String elderFilter(Auth.User u, List<Object> p);

    String claimFilter(Auth.User user, List<Object> parameters);

    String organizationFilter(Auth.User user, List<Object> parameters, String column);

    String orderFilter(Auth.User user, List<Object> parameters);

    String workerFilter(Auth.User user, List<Object> parameters);
}
