package com.example.dataprocess.infrastructure.runtime;

import com.example.dataprocess.application.state.DataProcessingGraphState;
import org.springframework.stereotype.Component;

/**
 * 技能执行状态持有器，用于在工具调用期间暴露当前图状态。
 */
@Component
public class SkillExecutionStateHolder {

    private final ThreadLocal<DataProcessingGraphState> stateHolder = new ThreadLocal<>();

    public void setCurrentState(DataProcessingGraphState state) {
        stateHolder.set(state);
    }

    public DataProcessingGraphState getRequiredCurrentState() {
        DataProcessingGraphState state = stateHolder.get();
        if (state == null) {
            throw new IllegalStateException("No current skill execution state is bound.");
        }
        return state;
    }

    public void clear() {
        stateHolder.remove();
    }
}
