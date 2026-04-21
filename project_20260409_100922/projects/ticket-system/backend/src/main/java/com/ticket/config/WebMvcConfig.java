package com.ticket.config;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置
 */
@Configuration
@EnableAspectJAutoProxy
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private AuthenticationInterceptor authenticationInterceptor;

    @Resource
    private PermissionInterceptor permissionInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 认证拦截器（先执行）
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/trains/**",
                        "/api/stations/**"
                );
        
        // 权限拦截器（后执行）
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/trains/**",
                        "/api/stations/**",
                        "/api/knowledge/list",
                        "/api/chat/**",
                        // 方案1：用户端自身数据操作接口加入白名单
                        "/api/user/**",
                        "/api/orders/**",
                        "/api/passengers/**"
                );
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 确保 Jackson 转换器存在，用于处理 application/json 请求
        boolean hasJackson = converters.stream()
                .anyMatch(converter -> converter instanceof MappingJackson2HttpMessageConverter);
        if (!hasJackson) {
            converters.add(new MappingJackson2HttpMessageConverter());
        }
    }
}
