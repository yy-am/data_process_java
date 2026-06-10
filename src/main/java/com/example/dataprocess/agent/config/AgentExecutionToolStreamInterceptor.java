package com.example.dataprocess.agent.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.example.dataprocess.agent.model.AgentWorkflowStage;
import com.example.dataprocess.agent.model.DataProcessingAgentState;
import com.example.dataprocess.agent.model.DataProcessingAgentStreamEvent;
import com.example.dataprocess.agent.service.AgentStreamEventPublisher;
import com.example.dataprocess.agent.tool.AgentStateTool;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits coarse-grained tool execution lifecycle events independently from
 * ReactAgent's aggregated message streaming.
 */
final class AgentExecutionToolStreamInterceptor extends ToolInterceptor {

    private final AgentStreamEventPublisher eventPublisher;
    private final AgentStateTool stateTool;

    AgentExecutionToolStreamInterceptor(
            AgentStreamEventPublisher eventPublisher,
            AgentStateTool stateTool
    ) {
        this.eventPublisher = eventPublisher;
        this.stateTool = stateTool;
    }

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        String taskId = request.getExecutionContext()
                .flatMap(executionContext -> executionContext.threadId())
                .orElse(null);

        emit(taskId, "TOOL_CALL", request.getToolName(), request.getToolCallId(),
                "开始调用工具: " + request.getToolName(), Map.of(
                        "toolName", request.getToolName(),
                        "toolCallId", safeValue(request.getToolCallId())
                ));

        try {
            ToolCallResponse response = handler.call(request);
            emit(taskId, "TOOL_RESULT", request.getToolName(), request.getToolCallId(),
                    "工具调用完成: " + request.getToolName(), Map.of(
                            "toolName", request.getToolName(),
                            "toolCallId", safeValue(request.getToolCallId()),
                            "status", safeValue(response == null ? null : response.getStatus()),
                            "error", response != null && response.isError()
                    ));
            return response;
        } catch (RuntimeException ex) {
            emit(taskId, "ERROR", request.getToolName(), request.getToolCallId(),
                    "工具调用失败: " + request.getToolName(), Map.of(
                            "toolName", request.getToolName(),
                            "toolCallId", safeValue(request.getToolCallId()),
                            "errorMessage", safeValue(ex.getMessage())
                    ));
            throw ex;
        }
    }

    @Override
    public String getName() {
        return "AgentExecutionToolStreamInterceptor";
    }

    private void emit(
            String taskId,
            String event,
            String toolName,
            String toolCallId,
            String message,
            Map<String, Object> detail
    ) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }

        AgentWorkflowStage stage = stateTool.loadTaskState(taskId)
                .map(DataProcessingAgentState::stage)
                .orElse(null);

        Map<String, Object> mergedDetail = new LinkedHashMap<>();
        if (detail != null) {
            mergedDetail.putAll(detail);
        }
        mergedDetail.put("toolName", safeValue(toolName));
        mergedDetail.put("toolCallId", safeValue(toolCallId));

        eventPublisher.emit(taskId, new DataProcessingAgentStreamEvent(
                event,
                taskId,
                stage,
                stage == null ? "" : stage.name(),
                "",
                message,
                null,
                mergedDetail
        ));
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }
}
