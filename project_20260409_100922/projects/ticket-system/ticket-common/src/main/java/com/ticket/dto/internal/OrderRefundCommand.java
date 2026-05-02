package com.ticket.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理端 / 内部服务发起的退票命令（跨服务边界 DTO）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRefundCommand {
    private String orderNo;
    private Long userId;
}
