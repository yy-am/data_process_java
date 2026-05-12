package com.example.dataprocess.infrastructure.runtime;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.example.dataprocess.application.workflow.DataProcessingStateGraphDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * StateGraph 相关 Bean 配置类。
 */
@Configuration
public class StateGraphConfig {

    @Bean
    public CompiledGraph dataProcessingCompiledGraph(
            DataProcessingStateGraphDefinition definition
    ) throws GraphStateException {
        return definition.build();
    }
}
