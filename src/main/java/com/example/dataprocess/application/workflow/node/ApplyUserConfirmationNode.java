package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.domain.model.UserConfirmationResult;
import com.example.dataprocess.infrastructure.service.StructuredConfirmationService;
import org.springframework.stereotype.Component;

/**
 * 用户确认结果应用节点。
 */
@Component
public class ApplyUserConfirmationNode {

    private final StructuredConfirmationService structuredConfirmationService;

    public ApplyUserConfirmationNode(StructuredConfirmationService structuredConfirmationService) {
        this.structuredConfirmationService = structuredConfirmationService;
    }

    /**
     * 校验前端提交的固定 JSON 结构，并转换为领域结果。
     */
    public UserConfirmationResult execute(DataProcessingGraphState state) {
        if (state.userConfirmationItems() == null) {
            throw new IllegalStateException("应用用户确认结果前必须先生成确认请求。");
        }
        if (state.userConfirmationRequest() == null) {
            throw new IllegalStateException("应用用户确认结果前必须先接收到确认请求。");
        }
        return structuredConfirmationService.applyConfirmationRequest(
                state.userConfirmationItems(),
                state.userConfirmationRequest()
        );
    }
}
