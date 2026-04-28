package com.ticket.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 全链路追踪配置
 * 绑定 application.yml 中的 trace 节点
 */
@Setter
@Getter
@Component
public class TraceConfig {

    /** 是否启用全链路追踪 */
    @Value("${trace.enabled}")
    private boolean enabled;

    /** 请求头中TraceID的字段名 */
    @Value("${trace.header-name}")
    private String headerName;

}
