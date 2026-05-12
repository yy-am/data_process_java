package com.example.dataprocess.infrastructure.runtime;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.example.dataprocess.infrastructure.tool.ConfirmationConstraintTool;
import com.example.dataprocess.infrastructure.tool.HeaderAliasTool;
import com.example.dataprocess.infrastructure.tool.InputSnapshotTool;
import com.example.dataprocess.infrastructure.tool.RuleDslTool;
import com.example.dataprocess.infrastructure.tool.TemplateCatalogTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 技能运行时配置，负责注册 skill，并按 skill 维度装配允许暴露的工具。
 */
@Configuration
public class SkillRuntimeConfig {

    @Bean
    public SkillRegistry skillRegistry(SkillRuntimeProperties properties) {
        return ClasspathSkillRegistry.builder()
                .classpathPath(properties.skillsClasspathPath())
                .build();
    }

    @Bean
    public Map<String, List<String>> skillAllowedTools() {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        mapping.put(
                "template-recognition",
                List.of("inputSnapshotTool", "templateCatalogTool", "headerAliasTool")
        );
        mapping.put(
                "confirmation-question",
                List.of("confirmationConstraintTool")
        );
        mapping.put(
                "rule-drafting",
                List.of("ruleDslTool")
        );
        return Map.copyOf(mapping);
    }

    @Bean
    public Map<String, List<ToolCallback>> groupedTools(
            Map<String, List<String>> skillAllowedTools,
            InputSnapshotTool inputSnapshotTool,
            TemplateCatalogTool templateCatalogTool,
            HeaderAliasTool headerAliasTool,
            ConfirmationConstraintTool confirmationConstraintTool,
            RuleDslTool ruleDslTool
    ) {
        ToolCallback[] allCallbacks = MethodToolCallbackProvider.builder()
                .toolObjects(
                        inputSnapshotTool,
                        templateCatalogTool,
                        headerAliasTool,
                        confirmationConstraintTool,
                        ruleDslTool
                )
                .build()
                .getToolCallbacks();

        Map<String, ToolCallback> callbacksByName = List.of(allCallbacks).stream()
                .collect(Collectors.toMap(
                        callback -> callback.getToolDefinition().name(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, List<ToolCallback>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : skillAllowedTools.entrySet()) {
            List<ToolCallback> callbacks = entry.getValue().stream()
                    .map(toolName -> {
                        ToolCallback callback = callbacksByName.get(toolName);
                        if (callback == null) {
                            throw new IllegalStateException("Missing tool callback: " + toolName);
                        }
                        return callback;
                    })
                    .toList();
            grouped.put(entry.getKey(), callbacks);
        }

        validateGroupedTools(grouped, skillAllowedTools);
        return Map.copyOf(grouped);
    }

    private void validateGroupedTools(
            Map<String, List<ToolCallback>> groupedTools,
            Map<String, List<String>> skillAllowedTools
    ) {
        for (Map.Entry<String, List<String>> entry : skillAllowedTools.entrySet()) {
            List<ToolCallback> callbacks = groupedTools.get(entry.getKey());
            if (callbacks == null) {
                throw new IllegalStateException("Missing grouped tools for skill: " + entry.getKey());
            }
            List<String> actualToolNames = callbacks.stream()
                    .map(callback -> callback.getToolDefinition().name())
                    .toList();
            if (!actualToolNames.equals(entry.getValue())) {
                throw new IllegalStateException(
                        "Grouped tools mismatch for skill %s, expected %s but found %s"
                                .formatted(entry.getKey(), entry.getValue(), actualToolNames)
                );
            }
        }
    }
}
