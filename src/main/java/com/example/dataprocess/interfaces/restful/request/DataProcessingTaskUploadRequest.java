package com.example.dataprocess.interfaces.restful.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * 前端上传 Excel 文件时使用的任务提交请求。
 *
 * @param taskId 任务唯一标识
 * @param excelFile 前端上传的 Excel 文件
 * @param sheetName 可选工作表名称，优先级高于 sheetIndex
 * @param sheetIndex 可选工作表下标，默认解析第一个工作表
 * @param sampleRowLimit 样例行抽样数量，默认 5 行
 */
public record DataProcessingTaskUploadRequest(
        @NotBlank String taskId,
        @NotNull MultipartFile excelFile,
        String sheetName,
        @Min(0) Integer sheetIndex,
        @Min(1) Integer sampleRowLimit
) {
}
