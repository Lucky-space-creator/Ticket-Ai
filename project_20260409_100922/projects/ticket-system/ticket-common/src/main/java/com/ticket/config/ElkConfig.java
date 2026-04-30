package com.ticket.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ELK日志采集配置
 * 绑定 application.yml 中的 elk 节点
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "elk")
public class ElkConfig {

    /** 是否启用ELK日志采集 */
    @Value("${elk.enabled:false}")
    private boolean enabled;

    /** 日志存储路径 */
    @Value("${elk.log-path:./logs}")
    private String logPath;

    /** Elasticsearch 子配置 */
    private Elasticsearch elasticsearch = new Elasticsearch();

    /** Logstash 子配置 */
    private Logstash logstash = new Logstash();

    /**
     * Elasticsearch 连接配置（供参考，应用端不直连ES，由Logstash采集）
     */
    @Setter
    @Getter
    public static class Elasticsearch {
        @Value("${elk.elasticsearch.host:localhost}")
        private String host;
        @Value("${elk.elasticsearch.port:9200}")
        private int port;
        @Value("${elk.elasticsearch.index-prefix:ticket-system}")
        private String indexPrefix;
        @Value("${elk.elasticsearch.ai-index-prefix:ticket-ai}")
        private String aiIndexPrefix;

    }

    /**
     * Logstash 配置信息（部署时需同步修改）
     */
    @Setter
    @Getter
    public static class Logstash {
        @Value("${elk.logstash.pipeline-path:./config/logstash.conf}")
        private String pipelinePath;

    }
}