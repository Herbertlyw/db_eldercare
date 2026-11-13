package edu.bjfu.eldercare.application.port;

import java.util.Map;

/** 业务模块显式声明其操作，不允许模块间使用字符串再次路由。 */
public interface ActionModule {

    Map<String, ActionHandler> actions();
}
