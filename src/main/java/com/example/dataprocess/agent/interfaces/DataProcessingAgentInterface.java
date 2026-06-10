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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

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
    public ResponseEntity<StreamingResponseBody> run(@Valid @RequestBody DataProcessingTaskRequest request) {
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

        StreamingResponseBody body = outputStream -> {
            try (Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                long[] sequence = {0L};
                log.info("Agent SSE stream subscribed, taskId={}", taskId);

                agentService.run(request)
                        .doOnNext(event -> emitMessage(writer, taskId, ++sequence[0], event))
                        .doOnError(ex -> log.error("Agent SSE stream failed, taskId={}", taskId, ex))
                        .doOnComplete(() -> log.info(
                                "Agent SSE stream completed, taskId={}, eventCount={}",
                                taskId,
                                sequence[0]
                        ))
                        .blockLast();

                log.info("Agent SSE response body closed, taskId={}, eventCount={}", taskId, sequence[0]);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

    private void emitMessage(
            Writer writer,
            String taskId,
            long sequence,
            DataProcessingAgentStreamEvent event
    ) {
        String eventId = taskId + "-" + sequence;
        String eventName = event.event() == null || event.event().isBlank() ? "MESSAGE" : event.event();
        try {
            writer.write("id: " + eventId + "\n");
            writer.write("event: " + eventName + "\n");
            writer.write("data: " + writeEventJson(event) + "\n\n");
            writer.flush();
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
            throw new UncheckedIOException(ex);
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
