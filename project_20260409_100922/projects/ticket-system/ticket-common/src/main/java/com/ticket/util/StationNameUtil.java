package com.ticket.util;

/**
 * 站名规范化：trim、全角空格与连续空白折叠，供 DB/Redis Key/路径搜索共用。
 */
public final class StationNameUtil {

    private StationNameUtil() {
    }

    /**
     * @param name 原始站名，可为 null
     * @return 规范化后的站名；null 输入视为空串
     */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim().replace('\u3000', ' ');
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    /** 两站是否同一站（按 {@link #normalize} 比较） */
    public static boolean sameStation(String a, String b) {
        return normalize(a).equals(normalize(b));
    }
}
