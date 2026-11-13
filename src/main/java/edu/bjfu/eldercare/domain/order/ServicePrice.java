package edu.bjfu.eldercare.domain.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 按次收取原价；按小时按预约分钟折算，保留两位小数。 */
public record ServicePrice(BigDecimal rate, Unit unit) {

    public enum Unit {
        VISIT,
        HOUR
    }

    public BigDecimal forMinutes(long minutes) {
        return unit == Unit.HOUR
                ? rate.multiply(BigDecimal.valueOf(minutes))
                        .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                : rate;
    }
}
