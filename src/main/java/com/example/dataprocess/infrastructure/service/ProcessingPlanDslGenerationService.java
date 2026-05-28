package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.DslGenerationContext;
import com.example.dataprocess.domain.model.ProcessingPlanDsl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 加工计划 DSL 生成服务。
 *
 * <p>它负责把已经确认好的 DSL 生成上下文交给 AI，并要求 AI 只生成目标列表达式级别的
 * SQL 片段。完整 INSERT、FROM、目标临时表等执行边界由后续系统代码拼接，不能交给 AI。</p>
 */
@Service
public class ProcessingPlanDslGenerationService {

    private static final String PROMPT_RESOURCE_PATH = "prompts/processing-plan-dsl-prompt.md";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final ProcessingPlanDslValidator processingPlanDslValidator;
    private final String systemPromptTemplate;
    private final String userPromptTemplate;

    public ProcessingPlanDslGenerationService(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            PromptTemplateService promptTemplateService,
            ProcessingPlanDslValidator processingPlanDslValidator
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.processingPlanDslValidator = processingPlanDslValidator;
        this.systemPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "System Prompt");
        this.userPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "User Prompt Template");
    }

    /**
     * 根据 DSL 生成上下文生成并校验加工计划 DSL。
     */
    public ProcessingPlanDsl generate(DslGenerationContext dslGenerationContext) {
        if (dslGenerationContext == null) {
            throw new IllegalArgumentException("生成加工计划 DSL 前必须提供 DSL 生成上下文。");
        }

        // AI 只负责把规则语义翻译成目标列表达式片段，不参与表名、写入目标或完整 SQL 拼接。
        ProcessingPlanDsl plan = ChatClient.create(chatModel)
                .prompt()
                .system(systemPromptTemplate)
                .user(buildUserPrompt(dslGenerationContext))
                .call()
                .entity(ProcessingPlanDsl.class);

        // 所有 AI 输出必须先经过安全校验，后续节点才能把表达式片段拼接进系统 SQL。
        return processingPlanDslValidator.validate(plan, dslGenerationContext);
    }

    /**
     * 构造 AI 用户提示词，只传入生成表达式所需的边界上下文。
     */
    private String buildUserPrompt(DslGenerationContext dslGenerationContext) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dslGenerationContext", dslGenerationContext);

        return userPromptTemplate.replace("{payload-json}", writeJson(payload));
    }

    /**
     * 将上下文序列化为 JSON，避免提示词里出现不稳定的对象字符串。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化加工计划 DSL 生成上下文失败。", ex);
        }
    }
}
