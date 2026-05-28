package com.example.dataprocess.agent.repository;

import com.example.dataprocess.agent.model.ParsedExcelFile;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory parsed file storage for early agent testing.
 */
@Repository
public class InMemoryParsedExcelFileRepository implements ParsedExcelFileRepository {

    private final Map<String, ParsedExcelFile> store = new ConcurrentHashMap<>();

    @Override
    public ParsedExcelFile save(ParsedExcelFile parsedExcelFile) {
        store.put(parsedExcelFile.parsedFileRef(), parsedExcelFile);
        return parsedExcelFile;
    }

    @Override
    public ParsedExcelFile findByRef(String parsedFileRef) {
        ParsedExcelFile parsedExcelFile = store.get(parsedFileRef);
        if (parsedExcelFile == null) {
            throw new IllegalArgumentException("未找到已解析 Excel 文件: " + parsedFileRef);
        }
        return parsedExcelFile;
    }
}
