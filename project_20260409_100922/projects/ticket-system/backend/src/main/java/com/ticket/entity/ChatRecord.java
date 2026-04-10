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
     * 类型 1-用户 2-机器人
     */
    private Integer msgType;

    /**
     * 置信度
     */
    private BigDecimal confidence;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
