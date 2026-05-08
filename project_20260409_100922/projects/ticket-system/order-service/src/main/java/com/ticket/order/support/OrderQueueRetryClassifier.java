package com.ticket.order.support;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * 判断异步排队消费失败是否应交给 MQ/Broker 重试（非业务失败）。
 */
public final class OrderQueueRetryClassifier {

    private OrderQueueRetryClassifier() {
    }

    public static boolean isRetryable(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TransientDataAccessException) {
                return true;
            }
            if (t instanceof QueryTimeoutException) {
                return true;
            }
            if (t instanceof DeadlockLoserDataAccessException) {
                return true;
            }
            if (t instanceof DataAccessResourceFailureException) {
                return true;
            }
            if (t instanceof org.springframework.jdbc.CannotGetJdbcConnectionException) {
                return true;
            }
            if (t instanceof RedisConnectionFailureException) {
                return true;
            }
            if (t.getClass().getName().endsWith("RedisCommandTimeoutException")) {
                return true;
            }
            if (t instanceof SocketTimeoutException) {
                return true;
            }
            if (t instanceof SocketException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("deadlock")) {
                    return true;
                }
                if (m.contains("timeout") && (m.contains("redis") || m.contains("jdbc") || m.contains("mysql"))) {
                    return true;
                }
                if (m.contains("connection reset") || m.contains("broken pipe")) {
                    return true;
                }
            }
        }
        return false;
    }
}
