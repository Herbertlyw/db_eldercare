package edu.bjfu.eldercare.application;

import edu.bjfu.eldercare.application.port.ActionHandler;
import edu.bjfu.eldercare.application.port.ActionModule;
import edu.bjfu.eldercare.application.port.AuditContext;
import edu.bjfu.eldercare.application.port.RecordAccess;
import edu.bjfu.eldercare.application.security.Auth;
import edu.bjfu.eldercare.domain.shared.Rule;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 写操作入口：只负责路由、审计身份和事务，不包含具体业务流程。 */
@Service
public class CareService {

    private final AuditContext audit;

    private final RecordAccess access;

    private final Map<String, ActionHandler> actions;

    public CareService(AuditContext audit, RecordAccess access, List<ActionModule> modules) {
        this.audit = audit;
        this.access = access;
        Map<String, ActionHandler> registered = new LinkedHashMap<>();
        for (ActionModule module : modules) {
            Routes.add(registered, module.actions());
        }
        actions = Map.copyOf(registered);
    }

    public Set<String> supportedActions() {
        return actions.keySet();
    }

    /** 保留原有档案访问校验入口。 */
    public void elderScope(Auth.User user, long elder) {
        access.elder(user, elder);
    }

    @Transactional
    public Object execute(Auth.User user, String action, Map<String, Object> input) {
        ActionHandler handler = actions.get(action);
        if (handler == null) {
            throw new Rule("未知业务操作");
        }
        audit.setActor(user.id() + ":" + user.name());
        return handler.handle(user, input);
    }
}
