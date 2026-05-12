package com.example.dataprocess.infrastructure.runtime;

import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能运行时配置类，负责装配技能注册表和分组工具。
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
            TemplateRecognitionGroupedTools templateRecognitionGroupedTools,
            ConfirmationQuestionGroupedTools confirmationQuestionGroupedTools,
            RuleDraftingGroupedTools ruleDraftingGroupedTools
    ) {
        ToolCallback[] templateTools = MethodToolCallbackProvider.builder()
                .toolObjects(templateRecognitionGroupedTools)
                .build()
                .getToolCallbacks();
        ToolCallback[] confirmationTools = MethodToolCallbackProvider.builder()
                .toolObjects(confirmationQuestionGroupedTools)
                .build()
                .getToolCallbacks();
        ToolCallback[] ruleTools = MethodToolCallbackProvider.builder()
                .toolObjects(ruleDraftingGroupedTools)
                .build()
                .getToolCallbacks();

        Map<String, List<ToolCallback>> grouped = Map.of(
                "template-recognition", Arrays.asList(templateTools),
                "confirmation-question", Arrays.asList(confirmationTools),
                "rule-drafting", Arrays.asList(ruleTools)
        );
        validateGroupedTools(grouped, skillAllowedTools);
        return grouped;
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
