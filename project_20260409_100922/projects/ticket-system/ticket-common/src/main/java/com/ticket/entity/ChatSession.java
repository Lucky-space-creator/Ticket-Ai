package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客服会话实体
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /**
     * 会话状态常量
     */
    public static final String STATUS_ACTIVE = "active";      // 进行中
    public static final String STATUS_PENDING = "pending";    // 等待接入
    public static final String STATUS_ENDED = "ended";        // 已结束
    public static final String STATUS_AI_ONLY = "ai_only";    // 仅AI对话

    /**
     * 会话ID（与 chat_record.session_id 对应）
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 用户ID（未登录为NULL）
     */
    private Long userId;

    /**
     * 当前接待客服员工ID
     */
    private Long employeeId;

    /**
     * 会话状态: active-进行中, pending-等待接入, ended-已结束, ai_only<!-- 仅AI对话 -->
     */
    private String status;

    /**
     * 会话标题/摘要
     */
    private String title;

    /**
     * 消息条数
     */
    private Integer messageCount;

    /**
     * 最后消息时间
     */
    private LocalDateTime lastMessageAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updatedAt;
}