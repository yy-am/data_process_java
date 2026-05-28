package com.example.dataprocess.agent.model;

/**
 * Null/blank inspection result for one original Excel column.
 */
public record ColumnNullInspectionResult(
        String actualColumn,
        boolean hasBlankValue
) {
}
