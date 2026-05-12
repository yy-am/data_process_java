package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.infrastructure.runtime.TemplateRecognitionSkillRuntime;
import org.springframework.stereotype.Component;

/**
 * 模板识别技能节点。
 */
@Component
public class TemplateRecognitionSkillNode {

    private final TemplateRecognitionSkillRuntime skillRuntime;

    public TemplateRecognitionSkillNode(TemplateRecognitionSkillRuntime skillRuntime) {
        this.skillRuntime = skillRuntime;
    }

    /**
     * 调用模板识别技能运行时返回结构化识别结果。
     */
    public TemplateRecognitionResult execute(DataProcessingGraphState state) {
        return skillRuntime.execute(state);
    }
}
