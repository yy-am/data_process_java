package com.example.dataprocess.interfaces.restful.request;

import com.example.dataprocess.domain.model.ParsedExcelContent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 数据加工任务的标准化启动请求。
 *
 * @param taskId 任务唯一标识，用于串联一次完整的 StateGraph 执行
 * @param inputType 输入来源类型，例如 excel-import
 * @param sourceHeaders 从输入文件中解析出的源表头列表
 * @param sampleRows 从输入文件中抽取的样例数据行，用于模板识别和规则生成
 */
public record DataProcessingTaskRequest(
        @NotBlank String taskId,
        @NotBlank String inputType,
        @NotEmpty List<String> sourceHeaders,
        @NotEmpty List<Map<String, String>> sampleRows
) {

    public static DataProcessingTaskRequest from(String taskId, ParsedExcelContent parsedExcelContent) {
        return new DataProcessingTaskRequest(
                taskId,
                parsedExcelContent.inputType(),
                parsedExcelContent.sourceHeaders(),
                parsedExcelContent.sampleRows()
        );
    }
}
