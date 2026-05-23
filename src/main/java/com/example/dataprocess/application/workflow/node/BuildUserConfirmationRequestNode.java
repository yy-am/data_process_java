package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.infrastructure.service.StructuredConfirmationService;
import org.springframework.stereotype.Component;

/**
 * 结构化用户确认请求生成节点。
 */
@Component
public class BuildUserConfirmationRequestNode {

    private final StructuredConfirmationService structuredConfirmationService;

    public BuildUserConfirmationRequestNode(StructuredConfirmationService structuredConfirmationService) {
        this.structuredConfirmationService = structuredConfirmationService;
    }

    /**
     * 根据模板识别结果构造固定 JSON 结构的确认请求。
     */
    public UserConfirmationItems execute(DataProcessingGraphState state) {
        return structuredConfirmationService.buildUserConfirmationItems(state.toTaskSession());
    }
}
