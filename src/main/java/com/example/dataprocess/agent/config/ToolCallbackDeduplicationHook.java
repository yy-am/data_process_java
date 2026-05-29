package com.example.dataprocess.agent.config;

import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Removes duplicate tool callbacks after skill-based dynamic tool injection.
 */
public class ToolCallbackDeduplicationHook extends AgentHook {

    private final ModelInterceptor interceptor = new ToolCallbackDeduplicationInterceptor();

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
        return List.of(interceptor);
    }

    @Override
    public String getName() {
        return "ToolCallbackDeduplicationHook";
    }

    private static final class ToolCallbackDeduplicationInterceptor extends ModelInterceptor {

        @Override
        public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
            ToolCallingChatOptions options = request.getOptions();
            List<ToolCallback> optionTools = options == null || options.getToolCallbacks() == null
                    ? List.of()
                    : options.getToolCallbacks();

            List<ToolCallback> dedupedOptionTools = dedupeByName(optionTools);
            if (options != null) {
                options.setToolCallbacks(dedupedOptionTools);
            }

            List<ToolCallback> dedupedDynamicTools = removeToolsAlreadyPresent(
                    dedupeByName(request.getDynamicToolCallbacks()),
                    dedupedOptionTools
            );

            ModelRequest dedupedRequest = ModelRequest.builder(request)
                    .options(options)
                    .tools(dedupeNames(request.getTools()))
                    .dynamicToolCallbacks(dedupedDynamicTools)
                    .build();
            return handler.call(dedupedRequest);
        }

        @Override
        public String getName() {
            return "ToolCallbackDeduplicationInterceptor";
        }

        private static List<ToolCallback> dedupeByName(List<ToolCallback> callbacks) {
            if (callbacks == null || callbacks.isEmpty()) {
                return List.of();
            }
            Map<String, ToolCallback> byName = new LinkedHashMap<>();
            for (ToolCallback callback : callbacks) {
                if (callback == null || callback.getToolDefinition() == null) {
                    continue;
                }
                byName.putIfAbsent(callback.getToolDefinition().name(), callback);
            }
            return new ArrayList<>(byName.values());
        }

        private static List<ToolCallback> removeToolsAlreadyPresent(
                List<ToolCallback> candidateTools,
                List<ToolCallback> existingTools
        ) {
            Map<String, ToolCallback> existingByName = new LinkedHashMap<>();
            for (ToolCallback callback : existingTools) {
                existingByName.put(callback.getToolDefinition().name(), callback);
            }

            List<ToolCallback> result = new ArrayList<>();
            for (ToolCallback callback : candidateTools) {
                if (!existingByName.containsKey(callback.getToolDefinition().name())) {
                    result.add(callback);
                }
            }
            return result;
        }

        private static List<String> dedupeNames(List<String> names) {
            if (names == null || names.isEmpty()) {
                return List.of();
            }
            return new ArrayList<>(new java.util.LinkedHashSet<>(names));
        }
    }
}
