package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.infrastructure.service.TemplateRecognitionService;
import org.springframework.stereotype.Component;

/**
 * 模板识别节点。
 */
@Component
public class TemplateRecognitionNode {

    private final TemplateRecognitionService templateRecognitionService;

    public TemplateRecognitionNode(TemplateRecognitionService templateRecognitionService) {
        this.templateRecognitionService = templateRecognitionService;
    }

    /**
     * 基于输入快照执行一次性模板识别。
     */
    public TemplateRecognitionResult execute(DataProcessingGraphState state) {
        return templateRecognitionService.recognize(state.inputSnapshot());
    }
}
