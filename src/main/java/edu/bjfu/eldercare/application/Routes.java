package edu.bjfu.eldercare.application;

import java.util.Map;

/** 启动时发现重复路由即失败，避免新实现静默覆盖旧实现。 */
final class Routes {

    private Routes() {}

    static <T> void add(Map<String, T> target, Map<String, T> additions) {
        additions.forEach(
                (name, handler) -> {
                    if (target.putIfAbsent(name, handler) != null) {
                        throw new IllegalStateException("重复路由: " + name);
                    }
                });
    }
}
