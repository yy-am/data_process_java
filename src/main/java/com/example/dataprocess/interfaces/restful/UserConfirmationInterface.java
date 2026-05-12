package com.example.dataprocess.interfaces.restful;

import com.example.dataprocess.application.workflow.DataProcessingStateGraphWorkflow;
import com.example.dataprocess.interfaces.restful.request.UserConfirmationRequest;
import com.example.dataprocess.interfaces.restful.response.UserConfirmationResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户确认结果提交接口。
 */
@RestController
@RequestMapping("/api/user-confirmation")
public class UserConfirmationInterface {

    private final DataProcessingStateGraphWorkflow workflow;

    public UserConfirmationInterface(DataProcessingStateGraphWorkflow workflow) {
        this.workflow = workflow;
    }

    /**
     * 提交用户确认结果并恢复工作流。
     */
    @PostMapping
    public UserConfirmationResponse confirm(@Valid @RequestBody UserConfirmationRequest request) {
        return workflow.resume(request);
    }
}
