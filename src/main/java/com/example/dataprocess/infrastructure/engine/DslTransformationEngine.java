package com.example.dataprocess.infrastructure.engine;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.TaskSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DSL 转换引擎，负责按 DSL 规则生成预览结果。
 */
@Component
public class DslTransformationEngine {

    private final ObjectMapper objectMapper;

    public DslTransformationEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 根据最终 DSL 将样例数据转换为目标预览结果。
     */
    public List<Map<String, String>> transform(TaskSession session, FinalDsl finalDsl) {
        try {
            Map<String, Object> dsl = objectMapper.readValue(
                    finalDsl.dslContent(),
                    new TypeReference<>() {
                    }
            );

            List<Map<String, Object>> mappings = objectMapper.convertValue(
                    dsl.getOrDefault("mappings", List.of()),
                    new TypeReference<>() {
                    }
            );
            Map<String, String> constants = objectMapper.convertValue(
                    dsl.getOrDefault("constants", Map.of()),
                    new TypeReference<>() {
                    }
            );

            List<Map<String, String>> previewRows = new ArrayList<>();
            for (Map<String, String> sourceRow : session.sampleRows()) {
                Map<String, String> targetRow = new LinkedHashMap<>();
                for (Map<String, Object> mapping : mappings) {
                    String targetField = String.valueOf(mapping.get("targetField"));
                    List<String> sourceFields = objectMapper.convertValue(
                            mapping.getOrDefault("sourceFields", List.of()),
                            new TypeReference<>() {
                            }
                    );
                    String value = sourceFields.stream()
                            .map(sourceRow::get)
                            .filter(v -> v != null && !v.isBlank())
                            .collect(Collectors.joining(" "));
                    targetRow.put(targetField, value);
                }
                targetRow.putAll(constants);
                previewRows.add(targetRow);
            }
            return previewRows;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to transform rows by DSL.", ex);
        }
    }
}
