package com.ticket.util;

import com.ticket.enums.ResponseCode;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回格式工具类
 */
public class ResponseUtil {

    /**
     * 成功返回（带数据）
     */
    public static <T> Result<T> success(T data) {
        return success(ResponseCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功返回（带消息和数据）
     */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResponseCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 成功返回（仅消息）
     */
    public static <T> Result<T> success(String message) {
        return success(message, null);
    }

    /**
     * 失败返回（使用错误码）
     */
    public static <T> Result<T> error(ResponseCode responseCode) {
        return new Result<>(responseCode.getCode(), responseCode.getMessage(), null);
    }

    /**
     * 失败返回（自定义消息）
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(ResponseCode.ERROR.getCode(), message, null);
    }

    /**
     * 失败返回（自定义错误码和消息）
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 统一返回结果对象
     */
    @Data
    @NoArgsConstructor
    public static class Result<T> {
        /**
         * 响应码
         */
        private Integer code;

        /**
         * 响应消息
         */
        private String message;

        /**
         * 响应数据
         */
        private T data;

        /**
         * 时间戳
         */
        private Long timestamp = System.currentTimeMillis();

        /**
         * 全参构造函数
         */
        public Result(Integer code, String message, T data, Long timestamp) {
            this.code = code;
            this.message = message;
            this.data = data;
            this.timestamp = timestamp != null ? timestamp : System.currentTimeMillis();
        }

        /**
         * 三参构造函数（code, message, data）
         */
        public Result(Integer code, String message, T data) {
            this.code = code;
            this.message = message;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
    }
}