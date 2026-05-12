package com.example.dataprocess.infrastructure.tool;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.interfaces.restful.request.FieldMappingDecisionDto;
import com.example.dataprocess.interfaces.restful.request.InputFieldDecisionDto;
import com.example.dataprocess.interfaces.restful.request.OptionFieldDecisionDto;
import com.example.dataprocess.interfaces.restful.request.UserConfirmationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则 DSL 工具，提供规则知识和 DSL 骨架上下文。
 */
@Component
public class RuleDslTool {

    private final ObjectMapper objectMapper;

    public RuleDslTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> loadRuleContext(String templateCode) {
        return Map.of(
                "templateCode", templateCode,
                "simpleRule", "Map A from the selected source field and write period and D as constants.",
                "dslSkeleton", Map.of(
                        "templateCode", templateCode,
                        "mappings", List.of(Map.of("targetField", "A", "sourceFields", List.of("invoice_no"))),
                        "constants", Map.of("period", "<user-select>", "D", "<user-input>")
                )
        );
    }

    public FinalDsl buildFallbackFinalDsl(UserConfirmationRequest request) {
        try {
            List<Map<String, Object>> mappings = request.mappingDecisions().stream()
                    .map(this::toMapping)
                    .toList();

            Map<String, String> constants = new LinkedHashMap<>();
            for (OptionFieldDecisionDto decision : request.optionFieldDecisions()) {
                constants.put(decision.fieldCode(), decision.selectedValue());
            }
            for (InputFieldDecisionDto decision : request.inputFieldDecisions()) {
                constants.put(decision.fieldCode(), decision.inputValue());
            }

            Map<String, Object> dsl = new LinkedHashMap<>();
            dsl.put("templateCode", request.templateCode());
            dsl.put("mappings", mappings);
            dsl.put("constants", constants);

            return new FinalDsl(
                    request.templateCode(),
                    objectMapper.writeValueAsString(dsl),
                    "Fallback DSL generated from user confirmation."
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to build fallback DSL.", ex);
        }
    }

    private Map<String, Object> toMapping(FieldMappingDecisionDto decision) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("targetField", decision.targetFieldCode());
        mapping.put("sourceFields", decision.selectedSourceFields());
        return mapping;
    }
}
