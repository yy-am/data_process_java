package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.FinalDsl;
import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.RuleContext;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.UserConfirmationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则 DSL 生成服务。
 *
 * <p>一期保持为单步 AI 服务。它只根据当前快照、模板识别结果、
 * 用户确认结果和显式规则上下文生成 DSL，不引入 agent，也不偷偷修补模型输出。</p>
 */
@Service
public class RuleDraftingService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final RuleContextService ruleContextService;

    public RuleDraftingService(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            RuleContextService ruleContextService
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.ruleContextService = ruleContextService;
    }

    /**
     * 根据模板识别结果和用户确认结果生成 DSL。
     */
    public FinalDsl draft(
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            UserConfirmationResult userConfirmationResult
    ) {
        if (templateRecognitionResult == null) {
            throw new IllegalStateException("生成 DSL 前必须先完成模板识别。");
        }

        RuleContext ruleContext = ruleContextService.loadRuleContext(templateRecognitionResult.templateCode());
        FinalDsl finalDsl = ChatClient.create(chatModel)
                .prompt()
                .system(buildSystemPrompt())
                .user(buildUserPrompt(inputSnapshot, templateRecognitionResult, userConfirmationResult, ruleContext))
                .call()
                .entity(FinalDsl.class);

        validateDraftResult(finalDsl, templateRecognitionResult.templateCode());
        return finalDsl;
    }

    /**
     * 构造 DSL 生成系统提示词。
     */
    private String buildSystemPrompt() {
        return """
                你是数据加工流程中的规则 DSL 生成服务。
                你的职责只有一件事：基于模板识别结果、用户确认结果和受控规则上下文，生成最终 DSL JSON 字符串。

                必须遵守以下约束：
                - 只输出 FinalDsl 对应的 JSON 字段。
                - dslContent 必须是一个合法 JSON 字符串。
                - DSL 顶层只允许 templateCode、mappings、constants 三个字段。
                - A 必须来自用户确认的映射结果。
                - period 和 D 必须写入 constants。
                - 不能引入隐藏字段、隐藏逻辑或额外 transform。
                """;
    }

    /**
     * 构造 DSL 生成用户提示词。
     */
    private String buildUserPrompt(
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            UserConfirmationResult userConfirmationResult,
            RuleContext ruleContext
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputSnapshot", inputSnapshot);
        payload.put("templateRecognitionResult", templateRecognitionResult);
        payload.put("userConfirmationResult", userConfirmationResult);
        payload.put("ruleContext", ruleContext);

        return """
                请基于下面的上下文生成最终 DSL，返回纯 JSON：
                %s
                """.formatted(writeJson(payload));
    }

    /**
     * 对 DSL 生成结果做最小显式校验。
     */
    private void validateDraftResult(FinalDsl finalDsl, String expectedTemplateCode) {
        if (finalDsl == null) {
            throw new IllegalStateException("DSL 生成服务未返回结果。");
        }
        if (finalDsl.templateCode() == null || !finalDsl.templateCode().equals(expectedTemplateCode)) {
            throw new IllegalStateException("DSL 结果中的 templateCode 与当前模板不一致。");
        }
        if (finalDsl.dslContent() == null || finalDsl.dslContent().isBlank()) {
            throw new IllegalStateException("DSL 结果缺少 dslContent。");
        }
    }

    /**
     * 序列化模型上下文。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化 DSL 生成上下文失败。", ex);
        }
    }
}
