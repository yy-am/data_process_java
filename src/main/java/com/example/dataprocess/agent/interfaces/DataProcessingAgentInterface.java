package com.example.dataprocess.agent.interfaces;

import com.example.dataprocess.agent.model.DataProcessingAgentStreamEvent;
import com.example.dataprocess.agent.model.ParsedExcelFile;
import com.example.dataprocess.agent.service.DataProcessingReactAgentService;
import com.example.dataprocess.agent.tool.ParsedExcelFileTool;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * REST entry points for the decoupled data-processing agent flow.
 */
@Validated
@RestController
@RequestMapping("/api/agent/data-processing")
public class DataProcessingAgentInterface {

    private static final Logger log = LoggerFactory.getLogger(DataProcessingAgentInterface.class);

    private final ParsedExcelFileTool parsedExcelFileTool;
    private final DataProcessingReactAgentService agentService;
    private final ObjectMapper objectMapper;

    public DataProcessingAgentInterface(
            ParsedExcelFileTool parsedExcelFileTool,
            DataProcessingReactAgentService agentService,
            ObjectMapper objectMapper
    ) {
        this.parsedExcelFileTool = parsedExcelFileTool;
        this.agentService = agentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/parsed-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsedFileUploadResponse parseFile(@Valid @ModelAttribute ParsedFileUploadRequest request) {
        ParsedExcelFile parsedExcelFile = parsedExcelFileTool.parseAndStore(
                request.excelFile(),
                request.sheetName(),
                request.sheetIndex(),
                request.sampleRowLimit()
        );
        return new ParsedFileUploadResponse(
                parsedExcelFile.parsedFileRef(),
                parsedExcelFile.taskId(),
                parsedExcelFile.inputType(),
                parsedExcelFile.sheetName(),
                parsedExcelFile.sourceHeaders(),
                parsedExcelFile.sampleRows(),
                parsedExcelFile.rows().size()
        );
    }

    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter run(@Valid @RequestBody DataProcessingTaskRequest request) {
        String taskId = request.taskId();
        String inputType = request.inputType();
        int sourceHeaderCount = request.sourceHeaders() == null ? 0 : request.sourceHeaders().size();
        int sampleRowCount = request.sampleRows() == null ? 0 : request.sampleRows().size();
        log.info(
                "Agent SSE request received, taskId={}, inputType={}, sourceHeaderCount={}, sampleRowCount={}",
                taskId,
                inputType,
                sourceHeaderCount,
                sampleRowCount
        );

        SseEmitter emitter = new SseEmitter(0L);
        AtomicLong sequence = new AtomicLong();
        log.info("Agent SSE emitter created, taskId={}", taskId);

        Disposable subscription = agentService.run(request).subscribe(
                event -> emitMessage(emitter, taskId, sequence.incrementAndGet(), event),
                ex -> {
                    log.error("Agent SSE stream failed, taskId={}", taskId, ex);
                    completeWithError(emitter, taskId, ex);
                },
                () -> {
                    log.info(
                            "Agent SSE stream completed, taskId={}, eventCount={}",
                            taskId,
                            sequence.get()
                    );
                    emitter.complete();
                }
        );

        emitter.onCompletion(() -> {
            log.info("Agent SSE emitter completed, taskId={}, eventCount={}", taskId, sequence.get());
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
        });
        emitter.onTimeout(() -> {
            log.warn("Agent SSE emitter timeout, taskId={}, eventCount={}", taskId, sequence.get());
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
            emitter.complete();
        });
        emitter.onError(ex -> {
            log.error("Agent SSE emitter error callback, taskId={}", taskId, ex);
            if (!subscription.isDisposed()) {
                subscription.dispose();
            }
        });

        return emitter;
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
                    .data(writeEventJson(event), MediaType.APPLICATION_JSON));
            log.info(
                    "Agent SSE event emitted, taskId={}, id={}, event={}, stage={}, textLength={}, detailKeys={}",
                    taskId,
                    eventId,
                    eventName,
                    event.stage(),
                    textLength(event),
                    event.detail() == null ? "[]" : event.detail().keySet()
            );
        } catch (IOException ex) {
            log.error("Agent SSE event send failed, taskId={}, id={}, event={}", taskId, eventId, eventName, ex);
            throw new IllegalStateException("Failed to send SSE event", ex);
        }
    }

    private void completeWithError(SseEmitter emitter, String taskId, Throwable ex) {
        try {
            emitter.completeWithError(ex);
        } catch (Exception completionEx) {
            log.warn("Agent SSE completeWithError failed, taskId={}", taskId, completionEx);
            emitter.complete();
        }
    }

    private String writeEventJson(DataProcessingAgentStreamEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize SSE event", ex);
        }
    }

    private int textLength(DataProcessingAgentStreamEvent event) {
        String text = event.message();
        return text == null ? 0 : text.length();
    }
}
