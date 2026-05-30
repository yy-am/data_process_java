package com.example.dataprocess.agent.interfaces;

import com.example.dataprocess.agent.model.ParsedExcelFile;
import com.example.dataprocess.agent.service.DataProcessingReactAgentService;
import com.example.dataprocess.agent.tool.ParsedExcelFileTool;
import com.example.dataprocess.interfaces.restful.request.DataProcessingTaskRequest;
import jakarta.validation.Valid;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REST entry points for the decoupled data-processing agent flow.
 */
@Validated
@RestController
@RequestMapping("/api/agent/data-processing")
public class DataProcessingAgentInterface {

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
    public Flux<AssistantMessage> run(@Valid @RequestBody DataProcessingTaskRequest request) {
        return agentService.run(request);
    }
}
