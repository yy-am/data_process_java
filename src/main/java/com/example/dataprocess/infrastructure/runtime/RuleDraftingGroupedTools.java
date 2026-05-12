package com.example.dataprocess.infrastructure.runtime;

import com.example.dataprocess.infrastructure.tool.RuleDslTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * rule-drafting 技能可见工具分组。
 */
@Component
public class RuleDraftingGroupedTools {

    private final SkillExecutionStateHolder stateHolder;
    private final RuleDslTool ruleDslTool;

    public RuleDraftingGroupedTools(
            SkillExecutionStateHolder stateHolder,
            RuleDslTool ruleDslTool
    ) {
        this.stateHolder = stateHolder;
        this.ruleDslTool = ruleDslTool;
    }

    @Tool(name = "ruleDslTool", description = "Load rule knowledge and DSL skeleton for the currently selected template.")
    public Map<String, Object> loadRuleContext() {
        return ruleDslTool.loadRuleContext(
                stateHolder.getRequiredCurrentState().templateRecognitionResult().templateCode()
        );
    }
}
