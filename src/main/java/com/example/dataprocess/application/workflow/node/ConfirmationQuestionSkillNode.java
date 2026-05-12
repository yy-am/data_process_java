package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.domain.model.UserConfirmationItems;
import com.example.dataprocess.infrastructure.runtime.ConfirmationQuestionSkillRuntime;
import org.springframework.stereotype.Component;

/**
 * 确认问题生成技能节点。
 */
@Component
public class ConfirmationQuestionSkillNode {

    private final ConfirmationQuestionSkillRuntime skillRuntime;

    public ConfirmationQuestionSkillNode(ConfirmationQuestionSkillRuntime skillRuntime) {
        this.skillRuntime = skillRuntime;
    }

    /**
     * 调用确认问题技能运行时生成确认项。
     */
    public UserConfirmationItems execute(DataProcessingGraphState state) {
        return skillRuntime.execute(state);
    }
}
