package com.ticket.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置
 * 拦截器执行顺序：SecurityFilter → RateLimitInterceptor → AuthenticationInterceptor → PermissionInterceptor
 */
@Configuration
@EnableAspectJAutoProxy
public class WebMvcConfig implements WebMvcConfigurer {

    private final ObjectProvider<AuthenticationInterceptor> authenticationInterceptorProvider;

    private final ObjectProvider<PermissionInterceptor> permissionInterceptorProvider;

    private final ObjectProvider<RateLimitInterceptor> rateLimitInterceptorProvider;

    private final ObjectProvider<SecurityFilter> securityFilterProvider;

    public WebMvcConfig(ObjectProvider<PermissionInterceptor> permissionInterceptorProvider,
                        ObjectProvider<RateLimitInterceptor> rateLimitInterceptorProvider,
                        ObjectProvider<AuthenticationInterceptor> authenticationInterceptorProvider,
                        ObjectProvider<SecurityFilter> securityFilterProvider) {
        this.permissionInterceptorProvider = permissionInterceptorProvider;
        this.rateLimitInterceptorProvider = rateLimitInterceptorProvider;
        this.authenticationInterceptorProvider = authenticationInterceptorProvider;
        this.securityFilterProvider = securityFilterProvider;
    }

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
        // 0. 限流拦截器（最先执行）
        RateLimitInterceptor rateLimitInterceptor = rateLimitInterceptorProvider.getIfAvailable();
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns(
                            "/api/auth/**",
                            "/api/trains/**",
                            "/api/stations/**"
                    );
        }

        // 1. 认证拦截器
        AuthenticationInterceptor authenticationInterceptor = authenticationInterceptorProvider.getIfAvailable();
        if (authenticationInterceptor != null) {
            registry.addInterceptor(authenticationInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns(
                            "/api/auth/**",
                            "/api/trains/**",
                            "/api/stations/**"
                    );
        }

        // 2. 权限拦截器
        PermissionInterceptor permissionInterceptor = permissionInterceptorProvider.getIfAvailable();
        if (permissionInterceptor != null) {
            registry.addInterceptor(permissionInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns(
                            "/api/auth/**",
                            "/api/trains/**",
                            "/api/stations/**",
                            "/api/knowledge/list",
                            "/api/chat/**",
                            "/api/user/**",
                            "/api/orders/**",
                            "/api/passengers/**",
                            "/api/customer-service/user-history"
                    );
        }
    }

    /**
     * 注册安全过滤器（优先级最高，在所有拦截器之前执行）
     */
    @Bean
    @ConditionalOnBean(SecurityFilter.class)
    public FilterRegistrationBean<SecurityFilter> securityFilterRegistration() {
        SecurityFilter securityFilter = securityFilterProvider.getObject();
        FilterRegistrationBean<SecurityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(securityFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1); // 最高优先级
        return registration;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        boolean hasJackson = converters.stream()
                .anyMatch(converter -> converter instanceof MappingJackson2HttpMessageConverter);
        if (!hasJackson) {
            converters.add(new MappingJackson2HttpMessageConverter());
        }
    }
}