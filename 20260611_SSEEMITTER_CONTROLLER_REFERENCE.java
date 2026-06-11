import com.example.dataprocess.agent.model.DataProcessingAgentStreamEvent;
import com.example.dataprocess.agent.service.DataProcessingReactAgentService;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reference controller fragment for SseEmitter-based agent streaming.
 *
 * Replace the corresponding methods in your actual controller.
 */
public final class SSEEmitterControllerReference {

    private final Logger logger;
    private final DataProcessingReactAgentService dataProcessingReactAgentService;

    public SSEEmitterControllerReference(
            Logger logger,
            DataProcessingReactAgentService dataProcessingReactAgentService
    ) {
        this.logger = logger;
        this.dataProcessingReactAgentService = dataProcessingReactAgentService;
    }

    private SseEmitter run(DataProcessingTaskRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        AtomicLong sequence = new AtomicLong();
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();

        Disposable subscription = dataProcessingReactAgentService.run(request)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSubscribe(ignoredSubscription ->
                        logger.info("Agent SSE stream subscribed, taskId={}", request.taskId()))
                .subscribe(
                        message -> emitMessage(emitter, request.taskId(), sequence.incrementAndGet(), message),
                        ex -> {
                            logger.error("Agent SSE stream failed, taskId={}", request.taskId(), ex);
                            emitter.completeWithError(ex);
                        },
                        () -> {
                            logger.info(
                                    "Agent SSE stream completed, taskId={}, eventCount={}",
                                    request.taskId(),
                                    sequence.get()
                            );
                            emitter.complete();
                        }
                );
        subscriptionRef.set(subscription);

        emitter.onCompletion(() -> {
            Disposable current = subscriptionRef.get();
            if (current != null && !current.isDisposed()) {
                current.dispose();
            }
            logger.info("Agent SSE emitter completed, taskId={}, eventCount={}", request.taskId(), sequence.get());
        });
        emitter.onTimeout(() -> {
            Disposable current = subscriptionRef.get();
            if (current != null && !current.isDisposed()) {
                current.dispose();
            }
            logger.warn("Agent SSE emitter timed out, taskId={}, eventCount={}", request.taskId(), sequence.get());
            emitter.complete();
        });
        emitter.onError(ex -> {
            Disposable current = subscriptionRef.get();
            if (current != null && !current.isDisposed()) {
                current.dispose();
            }
            logger.error("Agent SSE emitter failed, taskId={}, eventCount={}", request.taskId(), sequence.get(), ex);
        });

        return emitter;
    }

    private ServerSentEvent<AssistantMessage> toServerSentEvent(
            String taskId,
            long sequence,
            AssistantMessage message
    ) {
        return ServerSentEvent.<AssistantMessage>builder()
                .id(taskId + "-" + sequence)
                .event(metadataValue(message.getMetadata(), "event", "MESSAGE"))
                .data(message)
                .build();
    }

    private void emitMessage(
            SseEmitter emitter,
            String taskId,
            long sequence,
            DataProcessingAgentStreamEvent event
    ) {
        String eventId = taskId + "-" + sequence;
        String eventName = event.event() == null || event.event().isBlank() ? "MESSAGE" : event.event();
        try {
            emitter.send(SseEmitter.event()
                    .id(eventId)
                    .name(eventName)
                    .data(event, MediaType.APPLICATION_JSON));
            logger.info(
                    "Agent SSE event emitted, taskId={}, id={}, event={}, stage={}, textLength={}, detailKeys={}",
                    taskId,
                    eventId,
                    eventName,
                    event.stage(),
                    textLength(event),
                    event.detail() == null ? "[]" : event.detail().keySet()
            );
        } catch (IOException | IllegalStateException ex) {
            logger.error("Agent SSE event send failed, taskId={}, id={}, event={}", taskId, eventId, eventName, ex);
            emitter.completeWithError(ex);
        }
    }

    private String metadataValue(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private int textLength(DataProcessingAgentStreamEvent event) {
        String text = event.message();
        return text == null ? 0 : text.length();
    }
}
