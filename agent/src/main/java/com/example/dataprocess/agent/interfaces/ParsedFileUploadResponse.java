package com.example.dataprocess.agent.interfaces;

import java.util.List;
import java.util.Map;

/**
 * Parsed file reference returned to agent tests.
 */
public record ParsedFileUploadResponse(
        String parsedFileRef,
        String taskId,
        String inputType,
        String sheetName,
        List<String> sourceHeaders,
        List<Map<String, String>> sampleRows,
        int totalRows
) {
}
