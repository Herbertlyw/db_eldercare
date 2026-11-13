package edu.bjfu.eldercare.domain.shared;

/** 课程演示使用的米制平面坐标，边界点属于围栏内。 */
public record Position(double x, double y) {

    public boolean within(Position center, double radius) {
        return Math.hypot(x - center.x, y - center.y) <= radius;
    }
}
