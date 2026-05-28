package com.example.dataprocess.agent.interfaces;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * Agent parsed file upload request.
 */
public record ParsedFileUploadRequest(
        @NotNull MultipartFile excelFile,
        String sheetName,
        @Min(0) Integer sheetIndex,
        @Min(1) Integer sampleRowLimit
) {
}
