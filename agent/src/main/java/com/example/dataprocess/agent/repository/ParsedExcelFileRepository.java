package com.example.dataprocess.agent.repository;

import com.example.dataprocess.agent.model.ParsedExcelFile;

/**
 * Parsed Excel file storage behind parsedFileRef.
 */
public interface ParsedExcelFileRepository {

    ParsedExcelFile save(ParsedExcelFile parsedExcelFile);

    ParsedExcelFile findByRef(String parsedFileRef);
}
