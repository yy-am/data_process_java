package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.StandardRequiredFields;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Tool facade for standard-template required field configuration.
 */
@Component
public class RequiredFieldTool {

    private static final String REQUIRED_FIELDS_RESOURCE_PATH = "agent/config/standard-template-required-fields.json";

    private final ObjectMapper objectMapper;

    public RequiredFieldTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StandardRequiredFields loadRequiredFields(String standardTemplateCode) {
        Map<String, List<String>> configuredFields = readConfiguredFields();
        return new StandardRequiredFields(
                standardTemplateCode,
                List.copyOf(configuredFields.getOrDefault(standardTemplateCode, List.of()))
        );
    }

    private Map<String, List<String>> readConfiguredFields() {
        try (var inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(REQUIRED_FIELDS_RESOURCE_PATH)) {
            if (inputStream == null) {
                return Map.of();
            }
            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {
                    }
            );
        } catch (IOException ex) {
            throw new IllegalStateException("读取标准模板必填字段配置失败: " + REQUIRED_FIELDS_RESOURCE_PATH, ex);
        }
    }
}
