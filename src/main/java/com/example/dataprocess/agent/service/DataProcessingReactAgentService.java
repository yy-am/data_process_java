package com.example.dataprocess.agent.service;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.example.dataprocess.agent.model.AgentWorkflowStage;
import com.example.dataprocess.agent.model.DataProcessingAgentResponse;
import com.example.dataprocess.agent.model.DataProcessingAgentState;
import com.example.dataprocess.agent.model.DataProcessingAgentStreamEvent;
import com.example.dataprocess.agent.model.ParsedExcelFile;
import com.example.dataprocess.agent.tool.AgentStateTool;
import com.example.dataprocess.agent.tool.ParsedExcelFileTool;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thin service wrapper around the Spring AI Alibaba ReactAgent.
 */
@Service
public class DataProcessingReactAgentService {

    private static final String AGENT_INTERNAL_MODEL_STREAMING_KEY = "_stream_";

    private final ReactAgent dataProcessingReactAgent;
    private final AgentStreamEventPublisher eventPublisher;
    private final AgentStateTool stateTool;
    private final ParsedExcelFileTool parsedExcelFileTool;
    private final ObjectMapper objectMapper;
    private final ThinkVisibleTextAdapter thinkVisibleTextAdapter;

    public DataProcessingReactAgentService(
            @Qualifier("dataProcessingReactAgent") ReactAgent dataProcessingReactAgent,
            AgentStreamEventPublisher eventPublisher,
            AgentStateTool stateTool,
            ParsedExcelFileTool parsedExcelFileTool,
            ObjectMapper objectMapper,
            ThinkVisibleTextAdapter thinkVisibleTextAdapter
    ) {
        this.dataProcessingReactAgent = dataProcessingReactAgent;
        this.eventPublisher = eventPublisher;
        this.stateTool = stateTool;
        this.parsedExcelFileTool = parsedExcelFileTool;
        this.objectMapper = objectMapper;
        this.thinkVisibleTextAdapter = thinkVisibleTextAdapter;
    }

    public Flux<DataProcessingAgentStreamEvent> run(DataProcessingTaskRequest request) {
        return Flux.defer(() -> {
            String taskId = request.taskId();
            String parsedFileRef = ensureParsedFileRef(request);
            AtomicReference<AssistantMessage> latestAssistantMessage = new AtomicReference<>();
            AtomicReference<OverAllState> latestState = new AtomicReference<>();

            RunnableConfig config = RunnableConfig.builder()
                    .threadId(taskId)
                    .addMetadata(AGENT_INTERNAL_MODEL_STREAMING_KEY, true)
                    .build();

            Flux<DataProcessingAgentStreamEvent> publishedEvents = eventPublisher.createStream(taskId);
            Flux<DataProcessingAgentStreamEvent> mainEvents;

            try {
                Flux<NodeOutput> agentStream = dataProcessingReactAgent.stream(
                        buildAgentInstruction(request, parsedFileRef),
                        config
                );

                mainEvents = Flux.concat(
                        Flux.just(toStreamEvent(taskId, assistantMessage(
                                "START",
                                taskId,
                                null,
                                "数据加工 Agent 开始运行。",
                                Map.of("parsedFileRef", parsedFileRef)
                        ))),
                        agentStream.concatMap(output -> Flux.fromIterable(toAssistantMessages(
                                        taskId,
                                        output,
                                        latestAssistantMessage,
                                        latestState
                                )))
                                .map(message -> toStreamEvent(taskId, message)),
                        Flux.defer(() -> Flux.just(toStreamEvent(taskId, toFinalMessage(
                                request,
                                parsedFileRef,
                                latestAssistantMessage.get(),
                                latestState.get()
                        ))))
                ).onErrorResume(ex -> Flux.just(toStreamEvent(
                        taskId,
                        toErrorMessage(request, parsedFileRef, ex)
                )));
            } catch (Exception ex) {
                mainEvents = Flux.just(toStreamEvent(taskId, toErrorMessage(request, parsedFileRef, ex)));
            }

            return Flux.merge(
                    publishedEvents,
                    mainEvents.doFinally(signalType -> {
                        eventPublisher.complete(taskId);
                        thinkVisibleTextAdapter.clearTask(taskId);
                    })
            );
        }).onErrorResume(ex -> Flux.just(toStreamEvent(
                request.taskId(),
                toErrorMessage(request, null, ex)
        )));
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
                必须严格按照 skill 中的运行步骤和分支规则调用工具。
                如果需要用户确认，保存状态并返回 USER_CONFIRMATION_REQUIRED。
                如果用户确认已完成或无需用户确认，继续按照 skill 完成后续流程。
                最终只输出 DataProcessingAgentResponse JSON，不要输出 Markdown 或额外解释。
                本次输入 JSON:
                %s
                """.formatted(writeJson(payload));
    }

    private DataProcessingAgentResponse parseResponse(String content) throws GraphRunnerException {
        String json = extractJson(content);
        try {
            return objectMapper.readValue(json, DataProcessingAgentResponse.class);
        } catch (JsonProcessingException ex) {
            throw new GraphRunnerException(
                    "ReactAgent 最终响应不是合法的 DataProcessingAgentResponse JSON: " + content,
                    ex
            );
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

    private List<AssistantMessage> toAssistantMessages(
            String taskId,
            NodeOutput output,
            AtomicReference<AssistantMessage> latestAssistantMessage,
            AtomicReference<OverAllState> latestState
    ) {
        latestState.set(output.state());
        latestAssistant(output.state()).ifPresent(latestAssistantMessage::set);

        if (!(output instanceof StreamingOutput<?> streamingOutput)) {
            return List.of();
        }

        Message message = streamingOutput.message();
        if (message instanceof AssistantMessage assistantMessage) {
            latestAssistantMessage.set(assistantMessage);
            return assistantMessages(taskId, output.node(), streamingOutput, assistantMessage);
        }

        return List.of();
    }

    private List<AssistantMessage> assistantMessages(
            String taskId,
            String node,
            StreamingOutput<?> output,
            AssistantMessage message
    ) {
        List<AssistantMessage> messages = new ArrayList<>();
        String text = thinkVisibleTextAdapter.adapt(taskId, node, message.getText());
        if (text != null && !text.isBlank()) {
            messages.add(assistantMessage(
                    output.getOutputType() == OutputType.AGENT_MODEL_STREAMING ? "MODEL_DELTA" : "MODEL_MESSAGE",
                    taskId,
                    node,
                    text,
                    Map.of("outputType", outputTypeName(output)),
                    message
            ));
        }
        return messages;
    }

    private AssistantMessage toFinalMessage(
            DataProcessingTaskRequest request,
            String parsedFileRef,
            AssistantMessage latestAssistantMessage,
            OverAllState latestState
    ) {
        DataProcessingAgentResponse response = resolveFinalResponse(
                request,
                parsedFileRef,
                latestAssistantMessage,
                latestState
        );
        return assistantMessage(
                "FINAL",
                request.taskId(),
                null,
                writeJson(response),
                Map.of("response", response)
        );
    }

    private DataProcessingAgentResponse resolveFinalResponse(
            DataProcessingTaskRequest request,
            String parsedFileRef,
            AssistantMessage latestAssistantMessage,
            OverAllState latestState
    ) {
        AssistantMessage assistantMessage = latestAssistantMessage;
        if (assistantMessage == null) {
            assistantMessage = latestAssistant(latestState).orElse(null);
        }

        if (assistantMessage != null && assistantMessage.getText() != null && !assistantMessage.getText().isBlank()) {
            try {
                return parseResponse(assistantMessage.getText());
            } catch (Exception ignored) {
                // Agent may stop after deterministic tool execution; persisted state is the fallback source.
            }
        }

        return stateTool.loadTaskState(request.taskId())
                .map(this::toResponse)
                .orElseGet(() -> new DataProcessingAgentResponse(
                        AgentWorkflowStage.FAILED,
                        request.taskId(),
                        parsedFileRef,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        Map.of(),
                        "REACT_AGENT_NO_FINAL_RESPONSE",
                        "ReactAgent 流式运行结束，但既没有最终响应，也没有可恢复的任务状态。"
                ));
    }

    private AssistantMessage toErrorMessage(
            DataProcessingTaskRequest request,
            String parsedFileRef,
            Throwable ex
    ) {
        DataProcessingAgentResponse response = toFailedResponse(request, parsedFileRef, ex);
        return assistantMessage(
                "ERROR",
                request.taskId(),
                null,
                writeJson(response),
                Map.of(
                        "errorCode", response.errorCode(),
                        "response", response
                )
        );
    }

    private DataProcessingAgentResponse toFailedResponse(
            DataProcessingTaskRequest request,
            String parsedFileRef,
            Throwable ex
    ) {
        if (isMissingAssistantMessageError(ex)) {
            return stateTool.loadTaskState(request.taskId())
                    .map(this::toResponse)
                    .orElseGet(() -> new DataProcessingAgentResponse(
                            AgentWorkflowStage.FAILED,
                            request.taskId(),
                            parsedFileRef,
                            null,
                            null,
                            List.of(),
                            List.of(),
                            Map.of(),
                            "REACT_AGENT_NO_ASSISTANT_MESSAGE",
                            "ReactAgent 未生成最终 AssistantMessage，且未找到可恢复的任务状态。"
                    ));
        }

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
                failedState.fieldBindingPlan(),
                failedState.confirmationItems(),
                failedState.userConfirmationResult(),
                failedState.summary(),
                "REACT_AGENT_RUN_FAILED",
                ex.getMessage()
        );
    }

    private Optional<AssistantMessage> latestAssistant(OverAllState state) {
        if (state == null) {
            return Optional.empty();
        }
        Object messages = state.value("messages").orElse(null);
        if (!(messages instanceof List<?> messageList)) {
            return Optional.empty();
        }
        for (int i = messageList.size() - 1; i >= 0; i--) {
            if (messageList.get(i) instanceof AssistantMessage assistantMessage) {
                return Optional.of(assistantMessage);
            }
        }
        return Optional.empty();
    }

    private String outputTypeName(StreamingOutput<?> output) {
        return output.getOutputType() == null ? "" : output.getOutputType().name();
    }

    private AssistantMessage assistantMessage(
            String event,
            String taskId,
            String node,
            String content,
            Map<String, Object> metadata
    ) {
        return assistantMessage(event, taskId, node, content, metadata, null);
    }

    private AssistantMessage assistantMessage(
            String event,
            String taskId,
            String node,
            String content,
            Map<String, Object> metadata,
            AssistantMessage source
    ) {
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (source != null && source.getMetadata() != null) {
            mergedMetadata.putAll(source.getMetadata());
        }
        mergedMetadata.put("event", event);
        mergedMetadata.put("taskId", taskId);
        if (node != null && !node.isBlank()) {
            mergedMetadata.put("node", node);
        }
        if (metadata != null) {
            mergedMetadata.putAll(metadata);
        }

        AssistantMessage.Builder builder = AssistantMessage.builder()
                .content(content == null ? "" : content)
                .properties(mergedMetadata);
        if (source != null) {
            builder.toolCalls(source.getToolCalls());
            builder.media(source.getMedia());
        }
        return builder.build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化 Agent 数据失败。", ex);
        }
    }

    private boolean isMissingAssistantMessageError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains("No AssitantMessage found")
                    || message.contains("No AssistantMessage found"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private DataProcessingAgentResponse toResponse(DataProcessingAgentState state) {
        return new DataProcessingAgentResponse(
                state.stage(),
                state.taskId(),
                state.parsedFileRef(),
                state.templateRecognitionResult(),
                state.fieldBindingPlan(),
                state.confirmationItems(),
                state.userConfirmationResult(),
                state.summary(),
                state.stage() == AgentWorkflowStage.FAILED ? "AGENT_TASK_FAILED" : "",
                responseMessage(state)
        );
    }

    private DataProcessingAgentStreamEvent toStreamEvent(String taskId, AssistantMessage message) {
        Map<String, Object> metadata = message.getMetadata() == null ? Map.of() : message.getMetadata();
        String event = metadataValue(metadata, "event", "MESSAGE");
        String node = metadataValue(metadata, "node", "");
        DataProcessingAgentResponse response = responseValue(metadata.get("response"));
        Optional<DataProcessingAgentState> currentState = stateTool.loadTaskState(taskId);
        AgentWorkflowStage stage = response != null
                ? response.stage()
                : currentState.map(DataProcessingAgentState::stage).orElse(null);
        return new DataProcessingAgentStreamEvent(
                event,
                taskId,
                stage,
                stage == null ? "" : stage.name(),
                node,
                message.getText(),
                response,
                eventDetail(metadata)
        );
    }

    private DataProcessingAgentResponse responseValue(Object value) {
        return value instanceof DataProcessingAgentResponse response ? response : null;
    }

    private Map<String, Object> eventDetail(Map<String, Object> metadata) {
        Map<String, Object> detail = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (!"event".equals(key)
                    && !"taskId".equals(key)
                    && !"node".equals(key)
                    && !"response".equals(key)) {
                detail.put(key, value);
            }
        });
        return detail;
    }

    private String metadataValue(Map<String, Object> metadata, String key, String defaultValue) {
        Object value = metadata.get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private String responseMessage(DataProcessingAgentState state) {
        return switch (state.stage()) {
            case RECEIVED -> "任务已接收。";
            case TASK_CONTEXT_READY -> "任务上下文已准备完成。";
            case TEMPLATE_CONTEXT_READY -> "模板上下文已准备完成。";
            case FIELD_BINDING_PLAN_READY -> "字段绑定计划已校验。";
            case CONFIRMATION_ANALYZED -> "确认项分析已完成。";
            case USER_CONFIRMATION_REQUIRED -> "等待用户确认。";
            case USER_CONFIRMED -> "用户确认阶段已完成。";
            case POST_CONFIRMATION_CONTEXT_READY -> "确认后的加工上下文已准备完成。";
            case SQL_GENERATION_CONTEXT_READY -> "SQL 生成上下文已准备完成。";
            case PROCESSING_SQL_RENDERED -> "完整 SQL 已生成，等待落表执行接入。";
            case RESULT_TABLE_WRITTEN -> "结果表已写入，等待导出 Excel。";
            case FAILED -> "任务失败。";
            case COMPLETED -> "任务已完成。";
        };
    }
}
