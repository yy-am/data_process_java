package com.example.dataprocess.agent.model;

import java.util.List;

/**
 * Required target columns for a standard template.
 */
public record StandardRequiredFields(
        String standardTemplateCode,
        List<String> requiredColumns
) {
}
