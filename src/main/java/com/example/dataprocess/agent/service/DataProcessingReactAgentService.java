package com.example.dataprocess.agent.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.example.dataprocess.agent.model.AgentWorkflowStage;
import com.example.dataprocess.agent.model.DataProcessingAgentResponse;
import com.example.dataprocess.agent.model.DataProcessingAgentState;
import com.example.dataprocess.agent.model.ParsedExcelFile;
import com.example.dataprocess.agent.tool.AgentStateTool;
import com.example.dataprocess.agent.tool.ParsedExcelFileTool;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin service wrapper around the Spring AI Alibaba ReactAgent.
 */
@Service
public class DataProcessingReactAgentService {

    private final ReactAgent dataProcessingReactAgent;
    private final AgentStateTool stateTool;
    private final ParsedExcelFileTool parsedExcelFileTool;
    private final ObjectMapper objectMapper;

    public DataProcessingReactAgentService(
            @Qualifier("dataProcessingReactAgent") ReactAgent dataProcessingReactAgent,
            AgentStateTool stateTool,
            ParsedExcelFileTool parsedExcelFileTool,
            ObjectMapper objectMapper
    ) {
        this.dataProcessingReactAgent = dataProcessingReactAgent;
        this.stateTool = stateTool;
        this.parsedExcelFileTool = parsedExcelFileTool;
        this.objectMapper = objectMapper;
    }

    public DataProcessingAgentResponse run(DataProcessingTaskRequest request) {
        String parsedFileRef = ensureParsedFileRef(request);
        try {
            AssistantMessage message = dataProcessingReactAgent.call(
                    buildAgentInstruction(request, parsedFileRef),
                    RunnableConfig.builder().threadId(request.taskId()).build()
            );
            return parseResponse(message.getText());
        } catch (Exception ex) {
            DataProcessingAgentState failedState = stateTool.loadTaskState(request.taskId())
                    .map(state -> stateTool.markTaskFailed(state, "REACT_AGENT_RUN_FAILED", ex.getMessage()))
                    .orElseGet(() -> stateTool.saveTaskState(
                            DataProcessingAgentState.initial(request.taskId(), parsedFileRef)
                                    .withStage(AgentWorkflowStage.FAILED)
                                    .addError("REACT_AGENT_RUN_FAILED: " + ex.getMessage())
                    ));
            return new DataProcessingAgentResponse(
                    AgentWorkflowStage.FAILED,
                    failedState.taskId(),
                    failedState.parsedFileRef(),
                    failedState.templateRecognitionResult(),
                    failedState.confirmationItems(),
                    failedState.userConfirmationResult(),
                    failedState.summary(),
                    "REACT_AGENT_RUN_FAILED",
                    ex.getMessage()
            );
        }
    }

    private String ensureParsedFileRef(DataProcessingTaskRequest request) {
        return stateTool.loadTaskState(request.taskId())
                .map(DataProcessingAgentState::parsedFileRef)
                .filter(value -> value != null && !value.isBlank())
                .orElseGet(() -> {
                    ParsedExcelFile parsedExcelFile = parsedExcelFileTool.storeTaskRequest(request);
                    return parsedExcelFile.parsedFileRef();
                });
    }

    private String buildAgentInstruction(DataProcessingTaskRequest request, String parsedFileRef) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", request.taskId());
        payload.put("parsedFileRef", parsedFileRef);
        payload.put("taskRequest", request);

        return """
                请作为数据加工 ReAct Agent 执行一次任务推进。

                必须先读取并遵守 skill: data-processing-agent-skill。
                必须严格按照 skill 中“运行流程”的步骤和分支调用工具。
                当前测试范围只允许推进到 USER_CONFIRMATION_REQUIRED 或 USER_CONFIRMED：
                - 如果需要用户确认，保存状态并返回 USER_CONFIRMATION_REQUIRED。
                - 如果不需要用户确认，保存状态并返回 USER_CONFIRMED。
                - 不要调用临时表、SQL 片段、SQL 拼接或写库工具。

                最终必须只输出 DataProcessingAgentResponse JSON，不能输出 Markdown 或解释文字。

                本次输入 JSON：
                %s
                """.formatted(writeJson(payload));
    }

    private DataProcessingAgentResponse parseResponse(String content) throws GraphRunnerException {
        String json = extractJson(content);
        try {
            return objectMapper.readValue(json, DataProcessingAgentResponse.class);
        } catch (JsonProcessingException ex) {
            throw new GraphRunnerException("ReactAgent 最终响应不是合法 DataProcessingAgentResponse JSON: " + content, ex);
        }
    }

    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("ReactAgent 返回内容为空。");
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstBrace = trimmed.indexOf('{');
            int lastBrace = trimmed.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return trimmed.substring(firstBrace, lastBrace + 1);
            }
        }
        return trimmed;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化 Agent 输入失败。", ex);
        }
    }
}
