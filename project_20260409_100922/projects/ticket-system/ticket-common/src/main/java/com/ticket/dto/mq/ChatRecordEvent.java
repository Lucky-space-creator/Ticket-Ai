package com.ticket.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI聊天记录事件消息
 * 异步保存聊天记录并广播WebSocket
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatRecordEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息唯一ID */
    private String messageId;

    /** 用户ID */
    private Long userId;

    /** 会话ID */
    private String sessionId;

    /** 消息内容 */
    private String message;

    /** 消息类型: user/robot/pending/ended */
    private String msgType;

    /** 客服员工ID（可选） */
    private Long employeeId;

    /** 置信度 */
    private BigDecimal confidence;

    /** AI输入Token数 */
    private int inputTokens;

    /** AI输出Token数 */
    private int outputTokens;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 全链路追踪ID */
    private String traceId;
}
