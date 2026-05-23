package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.infrastructure.service.RuleDraftingService;
import org.springframework.stereotype.Component;

/**
 * 规则 DSL 生成节点。
 */
@Component
public class RuleDraftingNode {

    private final RuleDraftingService ruleDraftingService;

    public RuleDraftingNode(RuleDraftingService ruleDraftingService) {
        this.ruleDraftingService = ruleDraftingService;
    }

    /**
     * 基于当前模板识别结果和用户确认结果生成最终 DSL。
     */
    public FinalDsl execute(DataProcessingGraphState state) {
        return ruleDraftingService.draft(
                state.inputSnapshot(),
                state.templateRecognitionResult(),
                state.userConfirmationResult()
        );
    }
}
