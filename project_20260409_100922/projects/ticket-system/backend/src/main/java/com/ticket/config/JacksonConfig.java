package com.ticket.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson 配置
 * 解决雪花算法 ID 等 Long 类型数据在前端的精度丢失问题
 */
@Configuration
public class JacksonConfig {

    /**
     * 自定义 ObjectMapper
     * 注册 Long 转 String 序列化器和 Java 8 时间模块
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册 Java 8 时间模块
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 忽略 JSON 中的未知字段
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 注册 Long 转 String 模块（解决雪花算法 ID 精度丢失）
        mapper.registerModule(getLongToStringModule());

        return mapper;
    }

    /**
     * 创建 Long 转 String 模块
     */
    private SimpleModule getLongToStringModule() {
        SimpleModule module = new SimpleModule("LongToStringModule");
        module.addSerializer(Long.class, new LongToStringSerializer());
        module.addSerializer(long.class, new LongToStringSerializer());
        return module;
    }

    /**
     * Long 类型转字符串序列化器
     * 解决 JavaScript 最大安全整数 9007199254740991 的精度丢失问题
     */
    public static class LongToStringSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws java.io.IOException {
            if (value != null) {
                gen.writeString(value.toString());
            } else {
                gen.writeNull();
            }
        }
    }
}
