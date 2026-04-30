package com.ticket.dto.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库同步事件消息
 * 触发知识库向量库的异步同步
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KnowledgeSyncEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息唯一ID */
    private String messageId;

    /** 同步类型: full=全量同步, increment=增量同步 */
    private String syncType;

    /** 触发来源：add/update/delete/manual */
    private String triggerSource;

    /** 触发时间戳 */
    private long timestamp;
}
