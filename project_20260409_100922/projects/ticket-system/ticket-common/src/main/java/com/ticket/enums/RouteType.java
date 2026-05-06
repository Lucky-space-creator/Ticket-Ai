package com.ticket.enums;

public final class RouteType {

    /** 单段直筒 */
    public static final String SINGLE = "SINGLE";
    /** 同车联程（多线段同一 train_no） */
    public static final String DIRECT = "DIRECT";
    /** 异车换乘 */
    public static final String TRANSFER = "TRANSFER";

    private RouteType() {
    }
}
