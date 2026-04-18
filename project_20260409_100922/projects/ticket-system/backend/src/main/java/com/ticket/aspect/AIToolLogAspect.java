package com.ticket.aspect;

import dev.langchain4j.agent.tool.Tool;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * AI 工具调用日志切面
 * 拦截所有带 @Tool 注解的方法，在终端输出格式化的调用信息
 */
@Aspect
@Component
public class AIToolLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AIToolLogAspect.class);
    
    // 敏感信息正则表达式
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\b\\d{17}[\\dXx]\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b1[3-9]\\d{9}\\b");
    
    /**
     * 拦截所有带 @Tool 注解的方法
     */
    @Around("@annotation(dev.langchain4j.agent.tool.Tool)")
    public Object logToolCall(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Tool toolAnnotation = method.getAnnotation(Tool.class);
        
        String toolName = method.getName();
        String toolDescription = toolAnnotation != null ? Arrays.toString(toolAnnotation.value()) : "";
        Object[] args = joinPoint.getArgs();
        String[] paramNames = signature.getParameterNames();
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        // 输出工具调用开始信息
        printToolCallStart(toolName, toolDescription, paramNames, args);
        
        try {
            // 执行原方法
            Object result = joinPoint.proceed();
            
            // 计算执行时间
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            // 输出工具调用完成信息
            printToolCallSuccess(toolName, elapsedTime, result);
            
            return result;
        } catch (Throwable throwable) {
            // 计算执行时间（即使失败）
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            // 输出工具调用失败信息
            printToolCallError(toolName, elapsedTime, throwable);
            
            throw throwable;
        }
    }
    
    /**
     * 输出工具调用开始信息
     */
    private void printToolCallStart(String toolName, String toolDescription, 
                                   String[] paramNames, Object[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[AI Tool Call] ═══════════════════════════════════════\n");
        sb.append("[AI Tool Call] 🔧 AI 正在调用工具: ").append(toolName).append("\n");
        
        if (toolDescription != null && !toolDescription.isEmpty()) {
            sb.append("[AI Tool Call] 📝 工具说明: ").append(toolDescription).append("\n");
        }
        
        if (paramNames != null && paramNames.length > 0) {
            sb.append("[AI Tool Call] 📋 调用参数:\n");
            for (int i = 0; i < paramNames.length; i++) {
                String paramName = paramNames[i];
                Object paramValue = i < args.length ? args[i] : null;
                String maskedValue = maskSensitiveInfo(paramName, paramValue);
                sb.append("[AI Tool Call]   - ").append(paramName).append(" = ").append(maskedValue).append("\n");
            }
        }
        
        sb.append("[AI Tool Call] ⏳ 开始执行...");
        System.out.println(sb.toString());
        log.info("AI Tool Call Started: {} with args: {}", toolName, Arrays.toString(args));
    }
    
    /**
     * 输出工具调用成功信息
     */
    private void printToolCallSuccess(String toolName, long elapsedTime, Object result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[AI Tool Call] ✅ 工具 [").append(toolName).append("] 执行完成\n");
        sb.append("[AI Tool Call] ⏱️  耗时: ").append(elapsedTime).append(" ms\n");
        
        // 处理返回结果
        String resultStr = result != null ? result.toString() : "null";
        if (resultStr.length() > 500) {
            resultStr = resultStr.substring(0, 500) + "... [结果过长，已截断]";
        }
        sb.append("[AI Tool Call] 📤 返回结果: ").append(maskSensitiveInfoInString(resultStr)).append("\n");
        sb.append("[AI Tool Call] ═══════════════════════════════════════");
        
        System.out.println(sb.toString());
        log.info("AI Tool Call Completed: {} in {} ms", toolName, elapsedTime);
    }
    
    /**
     * 输出工具调用失败信息
     */
    private void printToolCallError(String toolName, long elapsedTime, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append("[AI Tool Call] ❌ 工具 [").append(toolName).append("] 执行失败\n");
        sb.append("[AI Tool Call] ⏱️  耗时: ").append(elapsedTime).append(" ms\n");
        sb.append("[AI Tool Call] 💥 错误信息: ").append(throwable.getClass().getSimpleName())
          .append(" - ").append(throwable.getMessage()).append("\n");
        sb.append("[AI Tool Call] ═══════════════════════════════════════");
        
        System.out.println(sb.toString());
        log.error("AI Tool Call Failed: {} in {} ms", toolName, elapsedTime, throwable);
    }
    
    /**
     * 根据参数名和值进行敏感信息脱敏
     */
    private String maskSensitiveInfo(String paramName, Object paramValue) {
        if (paramValue == null) {
            return "null";
        }
        
        String valueStr = paramValue.toString();
        
        // 根据参数名判断是否需要脱敏
        if (paramName.toLowerCase().contains("idcard") || 
            paramName.toLowerCase().contains("idno") ||
            paramName.toLowerCase().contains("idnum") ||
            paramName.toLowerCase().contains("cardno")) {
            return maskIdCard(valueStr);
        }
        
        if (paramName.toLowerCase().contains("phone") || 
            paramName.toLowerCase().contains("mobile") ||
            paramName.toLowerCase().contains("tel")) {
            return maskPhone(valueStr);
        }
        
        // 如果参数名没有明确标识，但内容匹配敏感信息模式，也进行脱敏
        return maskSensitiveInfoInString(valueStr);
    }
    
    /**
     * 在字符串中查找并脱敏敏感信息
     */
    private String maskSensitiveInfoInString(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        
        // 脱敏身份证号
        String result = ID_CARD_PATTERN.matcher(str).replaceAll(match -> {
            String idCard = match.group();
            return maskIdCard(idCard);
        });
        
        // 脱敏手机号
        result = PHONE_PATTERN.matcher(result).replaceAll(match -> {
            String phone = match.group();
            return maskPhone(phone);
        });
        
        return result;
    }
    
    /**
     * 脱敏身份证号（保留前4位和后4位）
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return "***";
        }
        int length = idCard.length();
        return idCard.substring(0, 4) + "********" + idCard.substring(length - 4);
    }
    
    /**
     * 脱敏手机号（保留前3位和后4位）
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        int length = phone.length();
        return phone.substring(0, 3) + "****" + phone.substring(length - 4);
    }
}