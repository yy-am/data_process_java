package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.infrastructure.engine.DslTransformationEngine;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * DSL 转换执行节点。
 */
@Component
public class DslTransformationNode {

    private final DslTransformationEngine dslTransformationEngine;

    public DslTransformationNode(DslTransformationEngine dslTransformationEngine) {
        this.dslTransformationEngine = dslTransformationEngine;
    }

    /**
     * 执行 DSL 转换并生成预览数据。
     */
    public List<Map<String, String>> execute(DataProcessingGraphState state) {
        return dslTransformationEngine.transform(state.toTaskSession(), state.finalDsl());
    }
}
