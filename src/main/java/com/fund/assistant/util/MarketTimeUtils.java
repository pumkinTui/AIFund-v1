package com.fund.assistant.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 交易时间工具类
 * A股：周一至周五 9:30-11:30, 13:00-15:00
 * 美股：周一至周五 21:30-次日04:00（夏令时，简化处理）
 */
public class MarketTimeUtils {

    public static boolean isTradingDay() {
        DayOfWeek dow = LocalDate.now().getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }

    /** A股是否在交易时段（9:30-11:30 或 13:00-15:00） */
    public static boolean isAStockTradingTime() {
        if (!isTradingDay()) return false;
        LocalTime now = LocalTime.now();
        return (now.isAfter(LocalTime.of(9, 29)) && now.isBefore(LocalTime.of(11, 31)))
                || (now.isAfter(LocalTime.of(12, 59)) && now.isBefore(LocalTime.of(15, 1)));
    }

    /** A股是否已收盘（15:00 之后） */
    public static boolean isAfterMarketClose() {
        if (!isTradingDay()) return true;
        return LocalTime.now().isAfter(LocalTime.of(15, 0));
    }

    /** 美股是否在交易时段（北京时间 21:30-次日04:00，夏令时简化） */
    public static boolean isUSStockTradingTime() {
        if (!isTradingDay()) return false;
        LocalTime now = LocalTime.now();
        // 美股夏令时：21:30-04:00
        return now.isAfter(LocalTime.of(21, 29)) || now.isBefore(LocalTime.of(4, 1));
    }

    /** 距离 A 股收盘剩余秒数 */
    public static long secondsUntilAClose() {
        LocalTime now = LocalTime.now();
        if (!isTradingDay() || now.isAfter(LocalTime.of(15, 30))) return 0;
        return java.time.Duration.between(now, LocalTime.of(15, 30)).getSeconds();
    }

    /** 距离午夜剩余秒数 */
    public static long secondsUntilMidnight() {
        return java.time.Duration.between(LocalTime.now(), LocalTime.of(23, 59, 59)).getSeconds() + 1;
    }
}
