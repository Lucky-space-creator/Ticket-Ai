package com.ticket.service;

import lombok.Data;

/**
 * 对账结果
 */
@Data
public class ReconciliationResult {

    /**
     * 对账成功的记录数
     */
    private int successCount;

    /**
     * 发现不一致的记录数
     */
    private int mismatchCount;

    /**
     * 修复的记录数
     */
    private int fixedCount;

    /**
     * 对账失败（异常）的记录数
     */
    private int errorCount;

    /**
     * 总记录数
     */
    private int totalCount;

    /**
     * 对账耗时（毫秒）
     */
    private long durationMs;

    /**
     * 错误信息摘要
     */
    private String errorSummary;

    public ReconciliationResult() {
        this.successCount = 0;
        this.mismatchCount = 0;
        this.fixedCount = 0;
        this.errorCount = 0;
        this.totalCount = 0;
        this.durationMs = 0;
        this.errorSummary = "";
    }

    /**
     * 增加成功计数
     */
    public void incrementSuccess() {
        successCount++;
        totalCount++;
    }

    /**
     * 增加不一致计数
     */
    public void incrementMismatch() {
        mismatchCount++;
        totalCount++;
    }

    /**
     * 增加修复计数
     */
    public void incrementFixed() {
        fixedCount++;
    }

    /**
     * 增加错误计数
     */
    public void incrementError() {
        errorCount++;
        totalCount++;
    }

    /**
     * 设置错误摘要
     */
    public void setErrorSummary(String summary) {
        this.errorSummary = summary;
    }
}