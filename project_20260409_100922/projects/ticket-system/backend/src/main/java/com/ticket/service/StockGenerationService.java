package com.ticket.service;

/**
 * 库存生成服务
 * 用于每日自动生成车票库存
 */
public interface StockGenerationService {

    /**
     * 生成未来指定天数的库存数据
     * @param days 生成未来多少天的库存（默认7天）
     * @return 生成结果：成功生成的车次数量、库存记录数量
     */
    StockGenerationResult generateStock(int days);

    /**
     * 为指定日期生成库存数据
     * @param trainDate 日期（格式：yyyy-MM-dd）
     * @return 生成结果：成功生成的车次数量、库存记录数量
     */
    StockGenerationResult generateStockForDate(String trainDate);

    /**
     * 生成库存结果类
     */
    class StockGenerationResult {
        private int trainCount;
        private int stockRecordCount;
        private int successCount;
        private int skipCount;
        private String message;

        public StockGenerationResult() {
        }

        public StockGenerationResult(int trainCount, int stockRecordCount, int successCount, int skipCount, String message) {
            this.trainCount = trainCount;
            this.stockRecordCount = stockRecordCount;
            this.successCount = successCount;
            this.skipCount = skipCount;
            this.message = message;
        }

        public int getTrainCount() {
            return trainCount;
        }

        public void setTrainCount(int trainCount) {
            this.trainCount = trainCount;
        }

        public int getStockRecordCount() {
            return stockRecordCount;
        }

        public void setStockRecordCount(int stockRecordCount) {
            this.stockRecordCount = stockRecordCount;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public void setSuccessCount(int successCount) {
            this.successCount = successCount;
        }

        public int getSkipCount() {
            return skipCount;
        }

        public void setSkipCount(int skipCount) {
            this.skipCount = skipCount;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("StockGenerationResult{trainCount=%d, stockRecordCount=%d, successCount=%d, skipCount=%d, message='%s'}",
                    trainCount, stockRecordCount, successCount, skipCount, message);
        }
    }
}