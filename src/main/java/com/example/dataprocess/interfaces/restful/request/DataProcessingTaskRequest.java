package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 数据加工任务提交请求。
 */
public record DataProcessingTaskRequest(
        @NotBlank String taskId,
        @NotEmpty List<String> sourceHeaders,
        @NotEmpty List<Map<String, String>> sampleRows
) {
}
