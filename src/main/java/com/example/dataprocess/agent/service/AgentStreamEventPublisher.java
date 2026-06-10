package com.example.dataprocess.agent.service;

import com.example.dataprocess.agent.model.DataProcessingAgentStreamEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task-scoped event publisher for agent runtime SSE events that must be emitted
 * independently from ReactAgent message aggregation timing.
 */
@Component
public class AgentStreamEventPublisher {

    private final Map<String, Sinks.Many<DataProcessingAgentStreamEvent>> sinks = new ConcurrentHashMap<>();

    public Flux<DataProcessingAgentStreamEvent> createStream(String taskId) {
        return sinks.computeIfAbsent(taskId, ignored -> Sinks.many().multicast().onBackpressureBuffer()).asFlux();
    }

    public void emit(String taskId, DataProcessingAgentStreamEvent event) {
        if (taskId == null || taskId.isBlank() || event == null) {
            return;
        }
        Sinks.Many<DataProcessingAgentStreamEvent> sink = sinks.computeIfAbsent(
                taskId,
                ignored -> Sinks.many().multicast().onBackpressureBuffer()
        );
        sink.tryEmitNext(event);
    }

    public void complete(String taskId) {
        Sinks.Many<DataProcessingAgentStreamEvent> sink = sinks.remove(taskId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }

    public void error(String taskId, Throwable ex) {
        Sinks.Many<DataProcessingAgentStreamEvent> sink = sinks.remove(taskId);
        if (sink != null) {
            sink.tryEmitError(ex);
        }
    }

    public void remove(String taskId) {
        sinks.remove(taskId);
    }
}
