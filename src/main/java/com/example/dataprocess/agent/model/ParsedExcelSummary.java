package com.example.dataprocess.agent.model;

import java.util.List;
import java.util.Map;

/**
 * Summary exposed to the agent before full staging.
 */
public record ParsedExcelSummary(
        String parsedFileRef,
        String inputType,
        String sheetName,
        List<String> sourceHeaders,
        List<Map<String, String>> sampleRows,
        String fullDataRef
) {
}
