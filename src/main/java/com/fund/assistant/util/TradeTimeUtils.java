package com.fund.assistant.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

public class TradeTimeUtils {

    // 15:00  cutoff  截止时间（基金下午3点前买，算当天；之后算第二天）
    private static final LocalTime CUTOFF_TIME = LocalTime.of(15, 0);
    // 普通基金
    public static final Byte FUND_TYPE_REGULAR = 1;
    // QDII 海外基金（T+2 确认）
    public static final Byte FUND_TYPE_QDII = 2;

    // 计算交易日期（T日）
    // 传入：用户下单时间（年月日 时分秒）
    // 返回：真正的基金交易日期（自动跳过节假日+周末）
    public static LocalDate calculateTradeDate(LocalDateTime orderTime) {
        // 判断：下单时间 是否 在15:00之前
        //toLocalTime把把 LocalDateTime（年月日 + 时分秒） 只提取出 时分秒部分，返回 LocalTime
        if (orderTime.toLocalTime().isBefore(CUTOFF_TIME)) {
            // 15点前  交易日期 = 当天
            return orderTime.toLocalDate();
        } else {
            // 15点后  从明天开始算
            LocalDate next = orderTime.toLocalDate().plusDays(1);
            // 如果明天是非交易日（周末/节假日），继续往后+1天
            while (isNonTradeDate(next)) {
                next = next.plusDays(1);
            }
            return next;
        }
    }


    //计算基金确认日期
    //tradeDate 交易日(T日)  fundType 基金类型
    //基金份额确认日期
    public static LocalDate calculateConfirmDate(LocalDate tradeDate, Byte fundType) {
        // 普通基金 T+1 确认；QDII基金 T+2 确认
        int offset = (fundType != null && fundType == FUND_TYPE_QDII) ? 2 : 1;
        LocalDate confirmDate = tradeDate.plusDays(offset);
        // 如果加完后是节假日/周末，继续往后顺延，直到找到交易日
        while (isNonTradeDate(confirmDate)) {
            confirmDate = confirmDate.plusDays(1);
        }
        return confirmDate;
    }

    //判断某一天是不是非交易日
    // 非交易日 = 周六/周日  或者  法定节假日
    public static boolean isNonTradeDate(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        //判断是不是周六、周日
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return true;
        }
        //判断是不是法定节假日（写死在CHINESE_HOLIDAYS里）
        return isChineseHoliday(date);
    }

    //判断是不是法定节假日
    // 仅内部调用，对外不开放
    private static boolean isChineseHoliday(LocalDate date) {
        return CHINESE_HOLIDAYS.contains(date);
    }

    // 获取下一个交易日
    // 传入任意一天，返回它之后最近的一个交易日
    public static LocalDate nextTradeDate(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (isNonTradeDate(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * 根据代码or名称自动判断基金类型：普通 or QDII
     */
    public static Byte determineFundType(String fundCode, String fundName) {
        if (fundCode == null && fundName == null) {
            return FUND_TYPE_REGULAR;
        }
        String upper = (fundCode != null ? fundCode : "") + (fundName != null ? fundName : "");
        if (upper.contains("QDII") || upper.contains("qdii")) {
            return FUND_TYPE_QDII;
        }
        return FUND_TYPE_REGULAR;
    }
    /**
     * 2026年法定节假日（写死配置）
     * 包含：元旦、春节、清明、劳动节、端午、中秋、国庆
     */
    private static final Set<LocalDate> CHINESE_HOLIDAYS = Set.of(
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 2),
        LocalDate.of(2026, 1, 3),
        LocalDate.of(2026, 1, 26),
        LocalDate.of(2026, 1, 27),
        LocalDate.of(2026, 1, 28),
        LocalDate.of(2026, 1, 29),
        LocalDate.of(2026, 1, 30),
        LocalDate.of(2026, 1, 31),
        LocalDate.of(2026, 2, 1),
        LocalDate.of(2026, 2, 2),
        LocalDate.of(2026, 2, 3),
        LocalDate.of(2026, 2, 4),
        LocalDate.of(2026, 2, 5),
        LocalDate.of(2026, 2, 6),
        LocalDate.of(2026, 4, 4),
        LocalDate.of(2026, 4, 5),
        LocalDate.of(2026, 4, 6),
        LocalDate.of(2026, 5, 1),
        LocalDate.of(2026, 5, 2),
        LocalDate.of(2026, 5, 3),
        LocalDate.of(2026, 5, 4),
        LocalDate.of(2026, 5, 5),
        LocalDate.of(2026, 6, 25),
        LocalDate.of(2026, 6, 26),
        LocalDate.of(2026, 6, 27),
        LocalDate.of(2026, 9, 26),
        LocalDate.of(2026, 9, 27),
        LocalDate.of(2026, 9, 28),
        LocalDate.of(2026, 10, 1),
        LocalDate.of(2026, 10, 2),
        LocalDate.of(2026, 10, 3),
        LocalDate.of(2026, 10, 4),
        LocalDate.of(2026, 10, 5),
        LocalDate.of(2026, 10, 6),
        LocalDate.of(2026, 10, 7),
        LocalDate.of(2026, 10, 8),
        LocalDate.of(2026, 10, 9)
    );
}
