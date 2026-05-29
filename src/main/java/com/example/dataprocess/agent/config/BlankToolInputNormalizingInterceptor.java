package com.example.dataprocess.agent.config;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;

/**
 * Aligns Agent tool execution with Spring AI's default blank-arguments behavior.
 */
final class BlankToolInputNormalizingInterceptor extends ToolInterceptor {

    private static final String EMPTY_JSON_OBJECT = "{}";

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        if (request.getArguments() != null && !request.getArguments().isBlank()) {
            return handler.call(request);
        }

        ToolCallRequest normalizedRequest = new ToolCallRequest(
                request.getToolName(),
                EMPTY_JSON_OBJECT,
                request.getToolCallId(),
                request.getContext(),
                request.getExecutionContext().orElse(null)
        );
        return handler.call(normalizedRequest);
    }

    @Override
    public String getName() {
        return "BlankToolInputNormalizing";
    }
}
