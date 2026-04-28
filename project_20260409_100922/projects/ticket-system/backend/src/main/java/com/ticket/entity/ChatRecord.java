package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客服对话记录实体
 */
@Data
@TableName("chat_record")
public class ChatRecord {

    /**
     * ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID（未登录为NULL）
     */
    private Long userId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 消息内容
     */
    private String message;

    /**
     * 消息类型: 'user'-用户消息, 'robot'-机器人回复, 员工号-客服消息
     */
    private String msgType;

    /**
     * 客服员工ID（仅当 msg_type 为员工号时有效）
     */
    private Long employeeId;

    /**
     * 是否已读 0-未读 1-已读
     */
    private Integer isRead;

    /**
     * 置信度
     */
    private BigDecimal confidence;

    /**
     * AI输入Token数（仅AI回复消息有效，用户消息为0）
     */
    private Integer inputTokens;

    /**
     * AI输出Token数（仅AI回复消息有效，用户消息为0）
     */
    private Integer outputTokens;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
