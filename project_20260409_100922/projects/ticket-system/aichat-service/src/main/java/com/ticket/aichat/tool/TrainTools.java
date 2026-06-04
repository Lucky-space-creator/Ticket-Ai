package com.ticket.aichat.tool;

import com.ticket.aichat.client.TrainClient;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 车次查询工具：搜索车次、查询车次详情。
 * <p>
 * 仅包含只读查询工具，供 TrainQueryAgent 使用。
 */
@Component
public class TrainTools {

    @Resource
    private TrainClient trainClient;

    @Tool("查询车次信息，根据出发地、目的地和日期返回可用车次列表，注意：参数必须是日期字符串（如 '2025-12-31'），不是数据库ID。")
    public List<Object> searchTrains(
            @P(value = "出发地", required = true) String from,
            @P(value = "目的地", required = true) String to,
            @P(value = "出发日期", required = true) String date) {
        Object data = ToolHelperUtil.feignCall("searchTrains", () -> ToolHelperUtil.unwrap(
                trainClient.searchTrainsPublic(
                        from != null ? from.trim() : "",
                        to != null ? to.trim() : "",
                        date != null ? date.trim() : ""),
                "查询车次"));
        if (data instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) list;
            return cast;
        }
        if (data instanceof Map<?, ?> m && Boolean.TRUE.equals(m.get("error"))) {
            return List.of(data);
        }
        return data == null ? List.of() : List.of(data);
    }

    @Tool("获取车次详情，根据车次号返回详细信息。注意：参数必须是车次号字符串（如 'G1234'），不是数据库ID。")
    public Object getTrainDetail(@P(value = "车次号", required = true) String trainNo) {
        String no = trainNo != null ? trainNo.trim() : "";
        return ToolHelperUtil.feignCall("getTrainDetail",
                () -> ToolHelperUtil.unwrap(trainClient.getTrainByTrainNo(no), "车次详情"));
    }
}
