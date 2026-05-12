package com.example.dataprocess.domain.model;

import java.util.List;
import java.util.Map;

/**
 * 标准化后的输入快照对象。
 */
public record InputSnapshot(
        String taskId,
        String inputType,
        List<String> normalizedHeaders,
        List<Map<String, String>> sampleRows
) {
}
