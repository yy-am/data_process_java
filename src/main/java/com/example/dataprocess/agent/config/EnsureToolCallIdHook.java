package com.example.dataprocess.agent.config;

import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ensures every tool call emitted by the model has a non-empty id so downstream
 * ToolResponseMessage construction remains valid in both streaming and non-streaming flows.
 */
public class EnsureToolCallIdHook extends AgentHook {

    private final ModelInterceptor interceptor = new EnsureToolCallIdInterceptor();

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
        return List.of(interceptor);
    }

    @Override
    public String getName() {
        return "EnsureToolCallIdHook";
    }

    private static final class EnsureToolCallIdInterceptor extends ModelInterceptor {

        @Override
        public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
            ModelResponse response = handler.call(request);
            if (response == null) {
                return null;
            }

            Object message = response.getMessage();
            if (message instanceof Flux<?> flux) {
                Flux<ChatResponse> fixedFlux = flux.cast(ChatResponse.class)
                        .map(EnsureToolCallIdInterceptor::ensureToolCallIds);
                return ModelResponse.of(fixedFlux);
            }

            if (message instanceof AssistantMessage assistantMessage) {
                AssistantMessage fixedMessage = ensureToolCallIds(assistantMessage);
                ChatResponse fixedChatResponse = ensureToolCallIds(response.getChatResponse());
                return ModelResponse.of(fixedMessage, fixedChatResponse);
            }

            return response;
        }

        @Override
        public String getName() {
            return "EnsureToolCallIdInterceptor";
        }

        private static ChatResponse ensureToolCallIds(ChatResponse response) {
            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return response;
            }

            boolean changed = false;
            List<Generation> fixedGenerations = new ArrayList<>(response.getResults().size());
            for (Generation generation : response.getResults()) {
                AssistantMessage originalMessage = generation.getOutput();
                AssistantMessage fixedMessage = ensureToolCallIds(originalMessage);
                if (fixedMessage != originalMessage) {
                    changed = true;
                    fixedGenerations.add(new Generation(fixedMessage, generation.getMetadata()));
                } else {
                    fixedGenerations.add(generation);
                }
            }

            if (!changed) {
                return response;
            }

            return ChatResponse.builder()
                    .from(response)
                    .generations(fixedGenerations)
                    .build();
        }

        private static AssistantMessage ensureToolCallIds(AssistantMessage message) {
            if (message == null || !message.hasToolCalls()) {
                return message;
            }

            boolean changed = false;
            List<AssistantMessage.ToolCall> fixedToolCalls = new ArrayList<>(message.getToolCalls().size());
            for (AssistantMessage.ToolCall toolCall : message.getToolCalls()) {
                if (toolCall == null) {
                    changed = true;
                    continue;
                }

                String toolCallId = toolCall.id();
                if (toolCallId == null || toolCallId.isBlank()) {
                    changed = true;
                    toolCallId = "call-" + UUID.randomUUID();
                }

                fixedToolCalls.add(new AssistantMessage.ToolCall(
                        toolCallId,
                        toolCall.type(),
                        toolCall.name(),
                        toolCall.arguments()
                ));
            }

            if (!changed) {
                return message;
            }

            return AssistantMessage.builder()
                    .content(message.getText() == null ? "" : message.getText())
                    .properties(message.getMetadata())
                    .toolCalls(List.copyOf(fixedToolCalls))
                    .media(message.getMedia())
                    .build();
        }
    }
}
