package com.ticket.aichat.tool;

import com.ticket.aichat.client.UserReadFeignClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人信息工具：管理用户资料、常用联系人。
 * <p>
 * 写操作（更新资料/添加/删除联系人）由策略禁止自动执行，供 ProfileAgent 使用。
 */
@Component
public class ProfileTools {

    @Resource
    private UserReadFeignClient userReadFeignClient;

    @Tool("更新个人信息，包括真实姓名和身份证号")
    public boolean updateProfile(
            @P(value = "真实姓名", required = true) String realName,
            @P(value = "身份证号", required = true) String idCard) {
        throw ToolHelperUtil.denyAutonomousWrite("updateProfile");
    }

    @Tool("获取常用联系人列表")
    public List<Object> getPassengers() {
        Object data = ToolHelperUtil.feignCall("getPassengers",
                () -> ToolHelperUtil.unwrap(userReadFeignClient.listPassengers(), "常用联系人"));
        if (data instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) list;
            return cast;
        }
        if (data instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("error"))) {
            return List.of(data);
        }
        return List.of();
    }

    @Tool("添加常用联系人")
    public Object addPassenger(
            @P(value = "姓名", required = true) String name,
            @P(value = "身份证号", required = true) String idCard,
            @P(value = "手机号", required = true) String phone) {
        throw ToolHelperUtil.denyAutonomousWrite("addPassenger");
    }

    @Tool("删除常用联系人")
    public boolean deletePassenger(@P(value = "联系人ID", required = true) Long passengerId) {
        throw ToolHelperUtil.denyAutonomousWrite("deletePassenger");
    }

    @Tool("获取当前用户个人信息")
    public Map<String, String> getUserProfile() {
        Object data = ToolHelperUtil.feignCall("getUserProfile",
                () -> ToolHelperUtil.unwrap(userReadFeignClient.getProfile(), "个人信息"));
        if (!(data instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        if (Boolean.TRUE.equals(raw.get("error"))) {
            return Map.of("error", raw.get("message") != null
                    ? String.valueOf(raw.get("message")) : "失败");
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            out.put(String.valueOf(e.getKey()),
                    e.getValue() != null ? String.valueOf(e.getValue()) : "");
        }
        return out;
    }
}
