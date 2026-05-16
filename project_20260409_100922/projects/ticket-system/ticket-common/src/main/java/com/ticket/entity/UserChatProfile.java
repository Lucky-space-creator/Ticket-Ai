package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户聊天画像实体
 */
@Data
@TableName("user_chat_profile")
public class UserChatProfile {

    /**
     * 主键（雪花ID）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 画像摘要文本
     */
    private String summary;

    /**
     * 总结时的消息条数
     */
    private Integer messageCount;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
