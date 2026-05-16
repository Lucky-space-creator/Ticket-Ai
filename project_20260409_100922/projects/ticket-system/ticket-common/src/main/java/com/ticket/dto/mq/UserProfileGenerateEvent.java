package com.ticket.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户画像生成事件
 * 触发异步画像总结和存储
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileGenerateEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息唯一ID（用于幂等） */
    private String messageId;

    /** 用户ID */
    private Long userId;

    /** 会话ID */
    private String sessionId;

    /** 触发来源：session_end（会话结束）/ message_threshold（消息阈值） */
    private String triggerSource;

    /** 触发时间戳 */
    private long timestamp;
}
