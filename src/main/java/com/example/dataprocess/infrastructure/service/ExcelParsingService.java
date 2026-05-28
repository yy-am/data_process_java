package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.ParsedExcelContent;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelParsingService {

    private static final int DEFAULT_SAMPLE_ROW_LIMIT = 5;

    public ParsedExcelContent parse(MultipartFile excelFile, String sheetName, Integer sheetIndex, Integer sampleRowLimit) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("Excel file must not be empty.");
        }

        try (InputStream inputStream = excelFile.getInputStream()) {
            return parse(inputStream, sheetName, sheetIndex, sampleRowLimit);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read uploaded Excel file.", ex);
        }
    }

    private ParsedExcelContent parse(InputStream inputStream, String sheetName, Integer sheetIndex, Integer sampleRowLimit) {
        int resolvedSampleRowLimit = sampleRowLimit == null || sampleRowLimit < 1
                ? DEFAULT_SAMPLE_ROW_LIMIT
                : sampleRowLimit;

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook, sheetName, sheetIndex);
            return extractSheetContent(sheet, resolvedSampleRowLimit);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse Excel file.", ex);
        }
    }

    private Sheet resolveSheet(Workbook workbook, String sheetName, Integer sheetIndex) {
        if (sheetName != null && !sheetName.isBlank()) {
            Sheet byName = workbook.getSheet(sheetName);
            if (byName == null) {
                throw new IllegalArgumentException("Specified sheet not found: " + sheetName);
            }
            return byName;
        }

        int resolvedSheetIndex = sheetIndex == null ? 0 : sheetIndex;
        if (resolvedSheetIndex < 0 || resolvedSheetIndex >= workbook.getNumberOfSheets()) {
            throw new IllegalArgumentException("Sheet index out of range: " + resolvedSheetIndex);
        }
        return workbook.getSheetAt(resolvedSheetIndex);
    }

    private ParsedExcelContent extractSheetContent(Sheet sheet, int sampleRowLimit) {
        DataFormatter formatter = new DataFormatter();
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            throw new IllegalArgumentException("Excel sheet is missing a header row.");
        }

        List<String> headers = extractHeaders(headerRow, formatter);
        if (headers.isEmpty()) {
            throw new IllegalArgumentException("Excel header row is empty.");
        }

        List<Map<String, String>> sampleRows = new ArrayList<>();
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= lastRowNum && sampleRows.size() < sampleRowLimit; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || isBlankRow(row, headers.size(), formatter)) {
                continue;
            }
            sampleRows.add(extractRow(row, headers, formatter));
        }

        return new ParsedExcelContent(
                "excel-import",
                sheet.getSheetName(),
                List.copyOf(headers),
                sampleRows.stream().map(Map::copyOf).toList()
        );
    }

    private List<String> extractHeaders(Row headerRow, DataFormatter formatter) {
        List<String> headers = new ArrayList<>();
        short lastCellNum = headerRow.getLastCellNum();
        if (lastCellNum <= 0) {
            return headers;
        }

        for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
            Cell headerCell = headerRow.getCell(columnIndex);
            String header = headerCell == null ? "" : formatter.formatCellValue(headerCell).trim();
            if (header.isEmpty()) {
                header = "column_" + (columnIndex + 1);
            }
            headers.add(header);
        }
        return headers;
    }

    private boolean isBlankRow(Row row, int headerSize, DataFormatter formatter) {
        for (int columnIndex = 0; columnIndex < headerSize; columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            if (cell != null && !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, String> extractRow(Row row, List<String> headers, DataFormatter formatter) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            Cell cell = row.getCell(columnIndex);
            values.put(headers.get(columnIndex), cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }
}
