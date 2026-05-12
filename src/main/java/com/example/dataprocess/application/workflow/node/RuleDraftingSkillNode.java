package com.example.dataprocess.application.workflow.node;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.infrastructure.runtime.RuleDraftingSkillRuntime;
import org.springframework.stereotype.Component;

/**
 * 规则草拟技能节点。
 */
@Component
public class RuleDraftingSkillNode {

    private final RuleDraftingSkillRuntime skillRuntime;

    public RuleDraftingSkillNode(RuleDraftingSkillRuntime skillRuntime) {
        this.skillRuntime = skillRuntime;
    }

    /**
     * 调用规则草拟技能运行时生成最终 DSL。
     */
    public FinalDsl execute(DataProcessingGraphState state) {
        return skillRuntime.execute(state);
    }
}
