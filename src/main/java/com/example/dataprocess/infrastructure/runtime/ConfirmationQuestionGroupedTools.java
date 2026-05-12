package com.example.dataprocess.infrastructure.runtime;

import com.example.dataprocess.infrastructure.tool.ConfirmationConstraintTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * confirmation-question 技能可见工具分组。
 */
@Component
public class ConfirmationQuestionGroupedTools {

    private final SkillExecutionStateHolder stateHolder;
    private final ConfirmationConstraintTool confirmationConstraintTool;

    public ConfirmationQuestionGroupedTools(
            SkillExecutionStateHolder stateHolder,
            ConfirmationConstraintTool confirmationConstraintTool
    ) {
        this.stateHolder = stateHolder;
        this.confirmationConstraintTool = confirmationConstraintTool;
    }

    @Tool(name = "confirmationConstraintTool", description = "Load the confirmation constraints and option ranges for the current task.")
    public Map<String, Object> loadConfirmationConstraints() {
        return confirmationConstraintTool.loadConfirmationConstraints(stateHolder.getRequiredCurrentState().toTaskSession());
    }

}
