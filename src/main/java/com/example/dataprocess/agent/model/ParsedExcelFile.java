package com.example.dataprocess.agent.model;

import java.util.List;
import java.util.Map;

/**
 * Parsed Excel content kept behind a parsedFileRef.
 */
public record ParsedExcelFile(
        String parsedFileRef,
        String taskId,
        String inputType,
        String sheetName,
        List<String> sourceHeaders,
        List<Map<String, String>> sampleRows,
        List<Map<String, String>> rows
) {
}
