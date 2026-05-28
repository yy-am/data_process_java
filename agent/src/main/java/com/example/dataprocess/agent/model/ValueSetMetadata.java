package com.example.dataprocess.agent.model;

import java.util.List;

/**
 * Value-set metadata needed by option confirmations.
 */
public record ValueSetMetadata(
        String targetColumn,
        String valueSetCode,
        List<String> optionValues
) {
}
