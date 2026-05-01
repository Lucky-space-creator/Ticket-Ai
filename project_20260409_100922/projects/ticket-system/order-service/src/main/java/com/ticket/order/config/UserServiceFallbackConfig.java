package com.ticket.order.config;

import com.ticket.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;
import java.util.Collections;

@Configuration
public class UserServiceFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(UserServiceFallbackConfig.class);

    @Bean
    @ConditionalOnMissingBean(UserService.class)
    public UserService userServiceFallback() {
        return (UserService) Proxy.newProxyInstance(
                UserService.class.getClassLoader(),
                new Class[]{UserService.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    switch (name) {
                        case "list":
                            return Collections.emptyList();
                        case "getById":
                        case "getByPhone":
                            return null;
                        case "register":
                        case "login":
                            throw new RuntimeException("user-service remote call is not configured");
                        case "updateProfile":
                        case "updateUserRole":
                            return false;
                        case "toString":
                            return "UserServiceFallbackProxy";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            log.warn("UserService fallback invoked: {}", name);
                            throw new UnsupportedOperationException("Unsupported UserService method: " + name);
                    }
                });
    }
}
