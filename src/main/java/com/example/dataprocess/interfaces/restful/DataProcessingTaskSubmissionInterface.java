package com.example.dataprocess.interfaces.restful;

import com.example.dataprocess.application.workflow.DataProcessingStateGraphWorkflow;
import com.example.dataprocess.domain.model.ParsedExcelContent;
import com.example.dataprocess.infrastructure.tool.ExcelParsingTool;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskUploadRequest;
import com.example.dataprocess.interfaces.restful.response.DataProcessingTaskResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据加工任务提交流量入口。
 */
@Validated
@RestController
@RequestMapping("/api/data-processing-tasks")
public class DataProcessingTaskSubmissionInterface {

    private final DataProcessingStateGraphWorkflow workflow;
    private final ExcelParsingTool excelParsingTool;

    public DataProcessingTaskSubmissionInterface(
            DataProcessingStateGraphWorkflow workflow,
            ExcelParsingTool excelParsingTool
    ) {
        this.workflow = workflow;
        this.excelParsingTool = excelParsingTool;
    }

    /**
     * 接收前端上传的 Excel 文件，解析成标准输入后启动工作流。
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DataProcessingTaskResponse submit(@Valid @ModelAttribute DataProcessingTaskUploadRequest request) {
        ParsedExcelContent parsedExcelContent = excelParsingTool.parse(
                request.excelFile(),
                request.sheetName(),
                request.sheetIndex(),
                request.sampleRowLimit()
        );

        return workflow.start(DataProcessingTaskRequest.from(request.taskId(), parsedExcelContent));
    }
}
