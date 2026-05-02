package com.ticket.config;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 安全过滤器（网关层防护第一道防线）
 * 功能：IP黑名单、SQL注入检测、XSS攻击检测、恶意扫描拦截
 */
@Component
public class SecurityFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(SecurityFilter.class);

    @Resource
    private GatewayConfig gatewayConfig;

    /** SQL注入特征正则 */
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i)(union.*select|select.*from|insert.*into|delete.*from|" +
            "drop.*table|update.*set|exec(ute)?\\s|script|alert\\s*\\(|" +
            "onload\\s*=|onerror\\s*=|'\\s*(or|and)\\s')",
            Pattern.CASE_INSENSITIVE
    );

    /** XSS特征正则 */
    private static final Pattern XSS_PATTERN = Pattern.compile(
            "(?i)(<script|</script|javascript:|vbscript:|on(load|error|click|mouse|focus|blur)\\s*=)",
            Pattern.CASE_INSENSITIVE
    );

    /** 路径穿越检测 */
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(
            "\\.\\.[/\\\\]|%2e%2e|%2e[/\\\\]|\\.\\.%2f"
    );

    /** 常见攻击User-Agent关键词 */
    private static final Set<String> MALICIOUS_UA_KEYWORDS = new HashSet<>();

    static {
        MALICIOUS_UA_KEYWORDS.add("sqlmap");
        MALICIOUS_UA_KEYWORDS.add("nikto");
        MALICIOUS_UA_KEYWORDS.add("nmap");
        MALICIOUS_UA_KEYWORDS.add("masscan");
        MALICIOUS_UA_KEYWORDS.add("dirbuster");
        MALICIOUS_UA_KEYWORDS.add("gobuster");
        MALICIOUS_UA_KEYWORDS.add("nuclei");
        // 不拦截 curl / python-requests：易误判健康检查与本地脚本，且 UA 可伪造
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!gatewayConfig.isSecurityEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // 1. IP黑名单检查
        if (isBlacklisted(clientIp)) {
            logger.warn("IP黑名单拦截: ip={}, uri={}, ua={}", clientIp, uri, userAgent);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeJsonResponse(response, 403, "访问被拒绝");
            return;
        }

        // 2. 恶意扫描器UA检测
        if (userAgent != null && isMaliciousUserAgent(userAgent)) {
            logger.warn("恶意扫描器UA拦截: ip={}, ua={}", clientIp, userAgent);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            writeJsonResponse(response, 403, "访问被拒绝");
            return;
        }

        // 3. 参数安全检查（仅对POST/PUT/PATCH请求体和查询参数检查）
        if (!checkParameters(request)) {
            logger.warn("非法参数拦截: ip={}, uri={}", clientIp, uri);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            writeJsonResponse(response, 400, "请求参数包含非法内容");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 检查IP是否在黑名单中
     */
    private boolean isBlacklisted(String ip) {
        if (ip == null || ip.isEmpty() || gatewayConfig.getBlacklistIps().isEmpty()) {
            return false;
        }
        for (String blackIp : gatewayConfig.getBlacklistIps()) {
            if (blackIp.equals(ip) || matchCidr(blackIp, ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单CIDR匹配（仅支持 /24 和 /32 等常见情况）
     */
    private boolean matchCidr(String cidr, String ip) {
        if (cidr.endsWith("/32")) {
            return cidr.split("/")[0].equals(ip);
        }
        if (cidr.endsWith("/24")) {
            String prefix = cidr.split("/")[0];
            String[] prefixParts = prefix.split("\\.");
            String[] ipParts = ip.split("\\.");
            if (prefixParts.length >= 3 && ipParts.length >= 3) {
                return prefixParts[0].equals(ipParts[0])
                        && prefixParts[1].equals(ipParts[1])
                        && prefixParts[2].equals(ipParts[2]);
            }
        }
        return false;
    }

    /**
     * 检查是否为恶意扫描器的User-Agent
     */
    private boolean isMaliciousUserAgent(String userAgent) {
        String uaLower = userAgent.toLowerCase();
        for (String keyword : MALICIOUS_UA_KEYWORDS) {
            if (uaLower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查所有请求参数是否包含非法内容
     */
    private boolean checkParameters(HttpServletRequest request) {
        // 检查Query参数
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            String value = request.getParameter(name);
            if (value != null && containsMaliciousContent(value)) {
                logger.debug("检测到非法参数: name={}, value={}", name, maskSensitive(value));
                return false;
            }
        }

        // 路径穿越检查
        if (PATH_TRAVERSAL_PATTERN.matcher(request.getRequestURI()).find()) {
            logger.debug("路径穿越检测: uri={}", request.getRequestURI());
            return false;
        }

        return true;
    }

    /**
     * 检测是否包含恶意内容（SQL注入/XSS）
     */
    private boolean containsMaliciousContent(String value) {
        if (value.length() > 4096) {
            // 过长的值可能为攻击payload，但正常业务也可能有长文本，此处仅记录不拦截
            logger.debug("参数值过长，长度: {}", value.length());
        }
        return SQL_INJECTION_PATTERN.matcher(value).find()
                || XSS_PATTERN.matcher(value).find();
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.contains(",") ? ip.split(",")[0].trim() : ip.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 敏感信息脱敏
     */
    private String maskSensitive(String value) {
        if (value == null || value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "***" + value.substring(value.length() - 4);
    }

    /**
     * 写入JSON错误响应
     */
    private void writeJsonResponse(HttpServletResponse response, int code, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        String json = String.format("{\"code\":%d,\"message\":\"%s\",\"timestamp\":%d}",
                code, message, System.currentTimeMillis());
        response.getWriter().write(json);
    }
}