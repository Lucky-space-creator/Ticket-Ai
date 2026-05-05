package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 手册向量治理记录（向量按 doc_id 删除、启停用过滤）。
 */
@Data
@TableName("kb_manual_document")
public class KbManualDocument {

    @TableId(value = "doc_id", type = IdType.INPUT)
    private String docId;

    @TableField("relative_path")
    private String relativePath;

    /** 1 启用向量检索参与；0 排除 */
    private Integer enabled;

    @TableField("content_checksum")
    private String contentChecksum;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
