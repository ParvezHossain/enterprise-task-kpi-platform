package com.parvez.kpi.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class KpiScoring {
    private KpiScoring() {
    }

    public static BigDecimal score(long total, long completed, long onTime, long overdue, double meanPriorityWeight) {
        if (total < 0 || completed < 0 || completed > total || onTime < 0 || onTime > completed || overdue < 0 || overdue > total || !Double.isFinite(meanPriorityWeight) || meanPriorityWeight < 0 || meanPriorityWeight > 4)
            throw new IllegalArgumentException("Invalid KPI counts");
        if (total == 0) return BigDecimal.ZERO.setScale(2);
        double value = 100 * (.4 * completed / total + .3 * (completed == 0 ? 0 : (double) onTime / completed) + .2 * Math.min(completed / 20.0, 1) + .1 * meanPriorityWeight / 4 - .2 * overdue / total);
        return BigDecimal.valueOf(Math.max(0, Math.min(100, value))).setScale(2, RoundingMode.HALF_UP);
    }
}
