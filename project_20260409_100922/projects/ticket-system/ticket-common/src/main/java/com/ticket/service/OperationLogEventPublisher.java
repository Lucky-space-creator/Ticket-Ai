package com.ticket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.annotation.OperationLog;
import com.ticket.dto.UserLoginRequest;
import com.ticket.dto.UserRegisterRequest;
import com.ticket.dto.mq.OperationLogEvent;
import com.ticket.entity.Employee;
import com.ticket.util.JwtUtil;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 操作日志事件发布器：把一次 HTTP 调用的元数据（操作者、URI、参数快照、耗时、成败）封装成 {@link OperationLogEvent}，
 * 通过 {@link RocketMQProducerService#sendOperationLogEvent} 异步发送，由 admin-service 消费写入 {@code operation_log}。
 * <p><b>设计要点</b>：</p>
 * <ul>
 *   <li>发送失败只打 warn，不影响业务接口返回值（审计旁路）。</li>
 *   <li>密码永不入参快照；手机号、身份证等做脱敏。</li>
 *   <li>登录前无 JWT：从请求体 / 表单取手机号掩码；登录成功后再从 {@link com.ticket.util.ResponseUtil.Result#getData()} 补全用户/员工 ID。</li>
 * </ul>
 */
@Slf4j
@Service
public class OperationLogEventPublisher {

    /** 与 {@link com.ticket.enums.ResponseCode#SUCCESS} 一致，用于判断 Result 是否为业务成功 */
    private static final int SUCCESS_CODE = 200;
    /** 入 MQ 前请求参数 JSON 总长度上限，防止单条消息过大 */
    private static final int MAX_PARAMS_LEN = 4000;
    /** 错误信息写入事件时的长度上限 */
    private static final int MAX_ERR_LEN = 2000;

    @Resource
    private RocketMQProducerService rocketMQProducerService;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 在切面中于目标方法执行后调用：根据返回值或异常组装事件并投递 MQ。
     *
     * @param meta        方法上的 {@link OperationLog} 注解
     * @param joinPoint   用于读取方法参数（构建脱敏后的 request_params）
     * @param returnValue 正常返回时的返回值；异常时为 null
     * @param thrown      业务抛出的异常；无异常时为 null
     * @param elapsedMs   方法耗时（毫秒），写入 {@code execution_time}
     */
    public void publishAfterOperation(OperationLog meta,
                                      ProceedingJoinPoint joinPoint,
                                      Object returnValue,
                                      Throwable thrown,
                                      long elapsedMs) {
        try {
            HttpServletRequest request = currentRequest();
            OperationLogEvent event = new OperationLogEvent();
            // 消费者幂等键，与 RocketMQ msgId 无关，由本端生成保证唯一
            event.setMessageId(UUID.randomUUID().toString().replace("-", ""));
            event.setModule(meta.module().getDisplayName());
            event.setOperation(meta.operation().getDisplayName());
            event.setDescription(StringUtils.hasText(meta.description())
                    ? meta.description()
                    : meta.operation().getDisplayName());
            event.setExecutionTime((int) Math.min(elapsedMs, Integer.MAX_VALUE));

            if (request != null) {
                event.setRequestMethod(request.getMethod());
                event.setRequestUrl(truncate(request.getRequestURI(), 500));
                event.setIpAddress(clientIp(request));
                event.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
            }

            // 优先从 JWT 解析操作者；登录类接口无 Token 时再走参数兜底
            resolveActor(event, request, joinPoint);
            event.setRequestParams(buildSafeParamsJson(request, joinPoint.getArgs()));

            if (thrown != null) {
                event.setStatus(0);
                event.setErrorMessage(truncate(thrown.getMessage(), MAX_ERR_LEN));
            } else if (returnValue instanceof com.ticket.util.ResponseUtil.Result<?> r) {
                Integer code = r.getCode();
                if (code != null && code.equals(SUCCESS_CODE)) {
                    event.setStatus(1);
                    // 登录成功时 JWT 尚未在部分场景写入上下文，从 Result.data 补 operator
                    enrichActorFromSuccessResponse(event, r);
                } else {
                    event.setStatus(0);
                    event.setErrorMessage(truncate(r.getMessage(), MAX_ERR_LEN));
                }
            } else {
                // 非统一 Result 包装时，视为成功（若需严格校验可改为失败或忽略日志）
                event.setStatus(1);
            }

            rocketMQProducerService.sendOperationLogEvent(event);
        } catch (Exception e) {
            log.warn("投递操作日志事件失败: {} - {}", meta.module().getDisplayName(),
                    meta.operation().getDisplayName(), e);
        }
    }

    /**
     * 解析「谁做的操作」写入 {@code user_id} / {@code username}。
     * <ul>
     *   <li>已携带合法 Bearer Token：员工写 employeeId + 展示名；普通用户写 userId + phone/名。</li>
     *   <li>无 Token（典型为登录）：从 {@link UserLoginRequest}、{@link UserRegisterRequest} 或表单参数 {@code phone} 取掩码手机号作 username。</li>
     * </ul>
     * 说明：管理端员工在库中与 C 端用户不同表，审计表里 {@code user_id} 对员工场景存的是 employeeId，属有意设计。
     */
    private void resolveActor(OperationLogEvent event, HttpServletRequest request, ProceedingJoinPoint joinPoint) {
        String token = extractBearer(request);
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            if (JwtUtil.USER_TYPE_EMPLOYEE.equals(jwtUtil.getUserTypeFromToken(token))) {
                Long id = jwtUtil.getEmployeeIdFromToken(token);
                event.setUserId(id);
                event.setUsername(truncate(jwtUtil.getDisplayNameFromToken(token), 50));
            } else {
                event.setUserId(jwtUtil.getUserIdFromToken(token));
                event.setUsername(truncate(jwtUtil.getDisplayNameFromToken(token), 50));
            }
            return;
        }
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof UserLoginRequest r && StringUtils.hasText(r.getPhone())) {
                event.setUsername(maskPhone(r.getPhone()));
                return;
            }
            if (arg instanceof UserRegisterRequest r && StringUtils.hasText(r.getPhone())) {
                event.setUsername(maskPhone(r.getPhone()));
                return;
            }
        }
        if (request != null) {
            String phone = request.getParameter("phone");
            if (StringUtils.hasText(phone)) {
                event.setUsername(maskPhone(phone));
            }
        }
    }

    /**
     * 当 resolveActor 阶段没有填全 ID（例如未登录调用），若业务返回 200 且 data 中含 employee/user 或直出 {@link Employee}，
     * 则补写操作者，便于事后审计追溯到具体账号。
     */
    private void enrichActorFromSuccessResponse(OperationLogEvent event, ResponseUtil.Result<?> r) {
        if (event.getUserId() != null && StringUtils.hasText(event.getUsername())) {
            return;
        }
        Object data = r.getData();
        if (data instanceof Employee em) {
            if (em.getId() != null) {
                event.setUserId(em.getId());
            }
            if (StringUtils.hasText(em.getName())) {
                event.setUsername(truncate(em.getName(), 50));
            } else if (StringUtils.hasText(em.getPhone())) {
                event.setUsername(maskPhone(em.getPhone()));
            }
            return;
        }
        if (!(data instanceof Map<?, ?> map)) {
            return;
        }
        Object employee = map.get("employee");
        if (employee instanceof Map<?, ?> em) {
            Object id = em.get("id");
            if (id instanceof Number) {
                event.setUserId(((Number) id).longValue());
            }
            Object name = em.get("name");
            if (name != null) {
                event.setUsername(truncate(String.valueOf(name), 50));
            } else if (em.get("phone") != null) {
                event.setUsername(maskPhone(String.valueOf(em.get("phone"))));
            }
            return;
        }
        Object user = map.get("user");
        if (user instanceof Map<?, ?> um) {
            Object id = um.get("id");
            if (id instanceof Number) {
                event.setUserId(((Number) id).longValue());
            }
            if (um.get("phone") != null) {
                event.setUsername(maskPhone(String.valueOf(um.get("phone"))));
            }
        }
    }

    /**
     * 构造写入 {@code request_params} 的 JSON：合并「登录类 URL 的表单参数」与「方法入参」两段信息。
     * 规则：跳过 Servlet API 对象；登录 DTO 只保留掩码手机号；其它对象转 Map 后删除 password、遮盖 idCard。
     */
    private String buildSafeParamsJson(HttpServletRequest request, Object[] args) {
        List<Object> sanitized = new ArrayList<>();
        // 兼容旧版 application/x-www-form-urlencoded 登录，JSON 请求此块通常为空
        if (request != null && request.getRequestURI() != null && request.getRequestURI().contains("/login")) {
            Map<String, String> form = new LinkedHashMap<>();
            request.getParameterMap().forEach((k, v) -> {
                if ("password".equalsIgnoreCase(k)) {
                    return;
                }
                String val = v != null && v.length > 0 ? v[0] : "";
                if ("phone".equalsIgnoreCase(k)) {
                    val = maskPhone(val);
                }
                form.put(k, val);
            });
            if (!form.isEmpty()) {
                sanitized.add(Map.of("form", form));
            }
        }
        if (args == null || args.length == 0) {
            return toParamsString(sanitized);
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (arg instanceof jakarta.servlet.ServletRequest
                    || arg instanceof jakarta.servlet.ServletResponse) {
                continue;
            }
            try {
                if (arg instanceof UserLoginRequest r) {
                    sanitized.add(Map.of("phone", maskPhone(r.getPhone())));
                    continue;
                }
                if (arg instanceof UserRegisterRequest r) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("phone", maskPhone(r.getPhone()));
                    m.put("realName", r.getRealName());
                    sanitized.add(m);
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>(
                        objectMapper.convertValue(arg, new TypeReference<Map<String, Object>>() {}));
                m.remove("password");
                if (m.containsKey("idCard")) {
                    m.put("idCard", "***");
                }
                sanitized.add(m);
            } catch (Exception e) {
                sanitized.add(arg.getClass().getSimpleName());
            }
        }
        return toParamsString(sanitized);
    }

    private String toParamsString(List<Object> sanitized) {
        if (sanitized.isEmpty()) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(sanitized), MAX_PARAMS_LEN);
        } catch (Exception e) {
            return null;
        }
    }

    /** 手机号中间掩码，满足审计「可关联不可还原全号」的常见合规要求 */
    private static String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * 优先取 X-Forwarded-For 第一个 IP（网关代理场景），否则 {@link HttpServletRequest#getRemoteAddr()}。
     */
    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            int comma = xff.indexOf(',');
            return comma > 0 ? xff.substring(0, comma).trim() : xff.trim();
        }
        return request.getRemoteAddr();
    }

    private static String extractBearer(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String h = request.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            return h.substring(7);
        }
        return null;
    }

    /** 必须在 Web 请求线程内调用（由 MVC 同步 Controller 触发），否则返回 null */
    private static HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }
}
