package com.ticket.util;

import cn.hutool.core.date.DateUtil;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 日期工具类
 */
public class DateUtils {

    /**
     * 日期时间格式
     */
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm:ss";

    /**
     * 获取当前日期时间
     */
    public static String now() {
        return format(LocalDateTime.now(), DATE_TIME_FORMAT);
    }

    /**
     * 获取当前日期
     */
    public static String today() {
        return format(LocalDate.now(), DATE_FORMAT);
    }

    /**
     * 格式化日期
     */
    public static String format(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        return sdf.format(date);
    }

    /**
     * 格式化 LocalDateTime
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return dateTime.format(formatter);
    }

    /**
     * 格式化 LocalDate
     */
    public static String format(LocalDate date, String pattern) {
        if (date == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date.format(formatter);
    }

    /**
     * 解析日期字符串
     */
    public static Date parse(String dateStr, String pattern) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        return DateUtil.parse(dateStr, pattern);
    }

    /**
     * 计算 N 天后的日期
     */
    public static String plusDays(int days) {
        return format(LocalDate.now().plusDays(days), DATE_FORMAT);
    }

    /**
     * 计算日期差（天数）
     */
    public static long daysBetween(String startDate, String endDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        return java.time.temporal.ChronoUnit.DAYS.between(start, end);
    }

    /**
     * 判断是否是未来日期
     */
    public static boolean isFuture(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        return date.isAfter(LocalDate.now());
    }

    /**
     * 判断是否是过去日期
     */
    public static boolean isPast(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        return date.isBefore(LocalDate.now());
    }

    /**
     * 判断是否是今天
     */
    public static boolean isToday(String dateStr) {
        LocalDate date = LocalDate.parse(dateStr);
        return date.isEqual(LocalDate.now());
    }
}