package com.ticket.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类
 * 处理缓存
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate Bean，用于 Redis 缓存操作
     * 配置了 Jackson2JsonRedisSerializer 进行 JSON 序列化，支持 Java 8 时间类型
     *
     * @param factory Redis 连接工厂
     * @return 配置完成的 RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 创建 ObjectMapper
        ObjectMapper mapper = new ObjectMapper();
        // 设置 ObjectMapper 的属性：强制 Jackson 能够访问所有类的字段（包括 private 字段），而不需要 getter/setter 方法
        //PropertyAccessor.ALL：应用于所有类型的属性（字段、getter、setter、构造器等）
        //JsonAutoDetect.Visibility.ANY：对所有可见性级别（private、protected、public）都进行检测
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        //在 JSON 中自动包含类型信息，解决多态序列化/反序列化时丢失具体类型的问题。
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);

        // 处理 Java 8 时间类型
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 使用 Jackson 序列化
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(mapper, Object.class);

        // 使用 StringRedisSerializer 来序列化 key
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // key 采用 String 的序列化方式
        template.setKeySerializer(stringSerializer);
        // hash 的 key 也采用 String 的序列化方式
        template.setHashKeySerializer(stringSerializer);

        // value 序列化方式采用 Jackson
        template.setValueSerializer(serializer);
        // hash 的 value 序列化方式采用 Jackson
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}