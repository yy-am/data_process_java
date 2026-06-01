package com.example.dataprocess.agent.interfaces;

import com.example.dataprocess.agent.model.ParsedExcelFile;
import com.example.dataprocess.agent.service.DataProcessingReactAgentService;
import com.example.dataprocess.agent.tool.ParsedExcelFileTool;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;
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

    public DataProcessingAgentInterface(
            ParsedExcelFileTool parsedExcelFileTool,
            DataProcessingReactAgentService agentService
    ) {
        this.parsedExcelFileTool = parsedExcelFileTool;
        this.agentService = agentService;
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
    public Flux<ServerSentEvent<AssistantMessage>> run(@Valid @RequestBody DataProcessingTaskRequest request) {
        AtomicLong sequence = new AtomicLong();
        log.info(
                "Agent SSE request received, taskId={}, inputType={}, sourceHeaderCount={}, sampleRowCount={}",
                request.taskId(),
                request.inputType(),
                request.sourceHeaders() == null ? 0 : request.sourceHeaders().size(),
                request.sampleRows() == null ? 0 : request.sampleRows().size()
        );
        return agentService.run(request)
                .doOnSubscribe(subscription -> log.info("Agent SSE stream subscribed, taskId={}", request.taskId()))
                .map(message -> {
                    ServerSentEvent<AssistantMessage> event = toServerSentEvent(
                            request.taskId(),
                            sequence.incrementAndGet(),
                            message
                    );
                    log.info(
                            "Agent SSE event emitted, taskId={}, id={}, event={}, textLength={}, metadataKeys={}",
                            request.taskId(),
                            event.id(),
                            event.event(),
                            textLength(message),
                            message.getMetadata() == null ? "[]" : message.getMetadata().keySet()
                    );
                    return event;
                })
                .doOnError(ex -> log.error("Agent SSE stream failed, taskId={}", request.taskId(), ex))
                .doOnComplete(() -> log.info(
                        "Agent SSE stream completed, taskId={}, eventCount={}",
                        request.taskId(),
                        sequence.get()
                ));
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

    private String metadataValue(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        Object value = metadata.get(key);
        return value == null || value.toString().isBlank() ? defaultValue : value.toString();
    }

    private int textLength(AssistantMessage message) {
        String text = message.getText();
        return text == null ? 0 : text.length();
    }
}
