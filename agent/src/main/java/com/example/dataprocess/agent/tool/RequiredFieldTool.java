package com.example.dataprocess.agent.tool;

import com.example.dataprocess.agent.model.StandardRequiredFields;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Tool facade for standard-template required field configuration.
 */
@Component
public class RequiredFieldTool {

    private static final Path REQUIRED_FIELDS_PATH = Path.of("agent", "config", "standard-template-required-fields.json");

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
        if (!Files.exists(REQUIRED_FIELDS_PATH)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    Files.readString(REQUIRED_FIELDS_PATH),
                    new TypeReference<>() {
                    }
            );
        } catch (IOException ex) {
            throw new IllegalStateException("读取标准模板必填字段配置失败: " + REQUIRED_FIELDS_PATH.toAbsolutePath(), ex);
        }
    }
}
