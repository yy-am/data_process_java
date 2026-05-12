package com.example.dataprocess.interfaces.restful;

import com.example.dataprocess.application.workflow.DataProcessingStateGraphWorkflow;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import com.example.dataprocess.interfaces.restful.response.DataProcessingTaskResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据加工任务提交接口。
 */
@RestController
@RequestMapping("/api/data-processing-tasks")
public class DataProcessingTaskSubmissionInterface {

    private final DataProcessingStateGraphWorkflow workflow;

    public DataProcessingTaskSubmissionInterface(DataProcessingStateGraphWorkflow workflow) {
        this.workflow = workflow;
    }

    /**
     * 提交数据加工任务并启动工作流。
     */
    @PostMapping
    public DataProcessingTaskResponse submit(@Valid @RequestBody DataProcessingTaskRequest request) {
        return workflow.start(request);
    }
}
