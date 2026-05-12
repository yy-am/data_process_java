package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import org.springframework.stereotype.Component;

/**
 * DSL 校验节点。
 */
@Component
public class DslValidationNode {

    /**
     * 判断当前 DSL 是否满足最小可执行条件。
     */
    public boolean isValid(DataProcessingGraphState state) {
        return state.finalDsl() != null
                && state.finalDsl().dslContent() != null
                && !state.finalDsl().dslContent().isBlank();
    }
}
