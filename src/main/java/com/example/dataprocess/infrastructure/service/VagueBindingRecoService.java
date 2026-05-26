package com.example.dataprocess.infrastructure.service;

import com.example.dataprocess.domain.model.InputSnapshot;
import com.example.dataprocess.domain.model.ProcessingRule;
import com.example.dataprocess.domain.model.ProcessingRuleItem;
import com.example.dataprocess.domain.model.TemplateRecognitionResult;
import com.example.dataprocess.domain.model.VagueBindingRecoItem;
import com.example.dataprocess.domain.model.VagueBindingRecoResult;
import com.example.dataprocess.domain.model.VagueBindingRecoStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 模糊字段绑定识别服务。
 *
 * <p>它只处理“规则源字段 sourceColumn 应绑定到本次上传 Excel 的哪个表头”这一件事，
 * 不负责用户选值/输入类确认，也不负责生成最终 DSL。</p>
 */
@Service
public class VagueBindingRecoService {

    private static final String PROMPT_RESOURCE_PATH = "prompts/vague-binding-reco-prompt.md";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final String systemPromptTemplate;
    private final String userPromptTemplate;

    public VagueBindingRecoService(
            ChatModel chatModel,
            ObjectMapper objectMapper,
            PromptTemplateService promptTemplateService
    ) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.systemPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "System Prompt");
        this.userPromptTemplate = promptTemplateService.loadPromptSection(PROMPT_RESOURCE_PATH, "User Prompt Template");
    }

    /**
     * 调用模型识别规则依赖字段与上传表头之间的绑定关系，并对模型输出做严格归一化校验。
     */
    public VagueBindingRecoResult recognize(
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            ProcessingRule processingRule
    ) {
        // 模型只基于输入快照、模板识别结果和规则文档做字段绑定判断。
        VagueBindingRecoResult result = ChatClient.create(chatModel)
                .prompt()
                .system(systemPromptTemplate)
                .user(buildUserPrompt(inputSnapshot, templateRecognitionResult, processingRule))
                .call()
                .entity(VagueBindingRecoResult.class);

        return normalizeAndValidate(result, inputSnapshot, templateRecognitionResult, processingRule);
    }

    /**
     * 构造模型用户提示词，把当前绑定识别所需上下文序列化为固定 JSON。
     */
    private String buildUserPrompt(
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            ProcessingRule processingRule
    ) {
        // 使用有序 Map，便于提示词和调试日志保持稳定字段顺序。
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("inputSnapshot", inputSnapshot);
        payload.put("templateRecognitionResult", templateRecognitionResult);
        payload.put("processingRule", processingRule);

        return userPromptTemplate.replace("{payload-json}", writeJson(payload));
    }

    /**
     * 校验模型返回的整体绑定结果，确保覆盖范围、任务标识和模板标识都与当前流程一致。
     */
    private VagueBindingRecoResult normalizeAndValidate(
            VagueBindingRecoResult result,
            InputSnapshot inputSnapshot,
            TemplateRecognitionResult templateRecognitionResult,
            ProcessingRule processingRule
    ) {
        if (result == null) {
            throw new IllegalStateException("Vague binding recognition did not return a result.");
        }
        if (!inputSnapshot.taskId().equals(result.taskId())) {
            throw new IllegalStateException("Vague binding recognition returned a mismatched taskId.");
        }
        if (!templateRecognitionResult.presetTemplateCode().equals(result.presetTemplateCode())) {
            throw new IllegalStateException("Vague binding recognition returned a mismatched presetTemplateCode.");
        }

        // 只有声明了 sourceColumns 的规则才需要做上传表头绑定识别。
        List<ProcessingRuleItem> ruleItemsWithSources = processingRule.ruleItems().stream()
                .filter(ruleItem -> !ruleItem.sourceColumns().isEmpty())
                .toList();
        // expectedKeys 表示规则文档要求模型必须覆盖的全部 target/rule/source 组合。
        Set<String> expectedKeys = ruleItemsWithSources.stream()
                .flatMap(ruleItem -> ruleItem.sourceColumns().stream()
                        .map(sourceColumn -> buildExpectedKey(ruleItem, sourceColumn)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // actualKeys 表示模型实际返回的组合，用于防止漏字段或编造字段。
        Set<String> actualKeys = result.items().stream()
                .map(this::buildActualKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!expectedKeys.equals(actualKeys)) {
            throw new IllegalStateException("Vague binding recognition coverage does not match rule dependencies.");
        }

        Set<String> availableHeaders = Set.copyOf(inputSnapshot.normalizedHeaders());
        // 后续逐条校验时，需要用目标列找回对应规则定义。
        Map<String, ProcessingRuleItem> ruleItemByTargetColumn = ruleItemsWithSources.stream()
                .collect(Collectors.toMap(ProcessingRuleItem::targetColumn, ruleItem -> ruleItem, (left, right) -> left));

        // 对每个识别项做状态和字段级别校验，并统一空集合/非法字段处理。
        List<VagueBindingRecoItem> normalizedItems = result.items().stream()
                .map(item -> normalizeAndValidateItem(item, availableHeaders, ruleItemByTargetColumn))
                .toList();

        return new VagueBindingRecoResult(
                result.taskId(),
                result.presetTemplateCode(),
                List.copyOf(normalizedItems)
        );
    }

    /**
     * 校验并归一化单条字段绑定识别结果。
     *
     * <p>不同状态有不同约束：已确认必须有 selectedHeader；待确认必须有候选表头；
     * 缺失则不能携带 selectedHeader 或候选表头。</p>
     */
    private VagueBindingRecoItem normalizeAndValidateItem(
            VagueBindingRecoItem item,
            Set<String> availableHeaders,
            Map<String, ProcessingRuleItem> ruleItemByTargetColumn
    ) {
        if (item == null) {
            throw new IllegalStateException("Vague binding recognition returned a null item.");
        }
        ProcessingRuleItem ruleItem = ruleItemByTargetColumn.get(item.targetColumn());
        if (ruleItem == null) {
            throw new IllegalStateException("Unknown targetColumn returned by vague binding recognition: " + item.targetColumn());
        }
        if (!ruleItem.ruleType().equals(item.ruleType())) {
            throw new IllegalStateException("ruleType does not match the rule document for targetColumn: " + item.targetColumn());
        }
        if (!ruleItem.sourceColumns().contains(item.sourceColumn())) {
            throw new IllegalStateException("sourceColumn is not declared by the rule document: " + item.sourceColumn());
        }

        // status 决定后续是否直接绑定、进入用户确认，或停止 DSL 生成。
        VagueBindingRecoStatus status = item.status();
        if (status == null) {
            throw new IllegalStateException("Vague binding recognition item is missing status.");
        }

        // 候选表头必须来自本次上传 Excel，不能由模型编造。
        List<String> candidateHeaders = item.candidateHeaders() == null ? List.of() : List.copyOf(item.candidateHeaders());
        for (String candidateHeader : candidateHeaders) {
            if (!availableHeaders.contains(candidateHeader)) {
                throw new IllegalStateException("candidateHeaders contains a header outside the uploaded headers: " + candidateHeader);
            }
        }

        String selectedHeader = item.selectedHeader();
        if (selectedHeader != null && !availableHeaders.contains(selectedHeader)) {
            throw new IllegalStateException("selectedHeader is outside the uploaded headers: " + selectedHeader);
        }

        // 根据识别状态收紧字段形态，保证写回 state 的结果是确定且一致的。
        return switch (status) {
            case CONFIRMED -> {
                if (selectedHeader == null || selectedHeader.isBlank()) {
                    throw new IllegalStateException("CONFIRMED item must contain selectedHeader.");
                }
                if (!candidateHeaders.isEmpty()) {
                    throw new IllegalStateException("CONFIRMED item must not contain candidateHeaders.");
                }
                yield new VagueBindingRecoItem(
                        item.targetColumn(),
                        item.ruleType(),
                        item.sourceColumn(),
                        status,
                        selectedHeader,
                        List.of(),
                        item.reason()
                );
            }
            case NEEDS_CONFIRMATION -> {
                if (selectedHeader != null) {
                    throw new IllegalStateException("NEEDS_CONFIRMATION item must not contain selectedHeader.");
                }
                if (candidateHeaders.size() < 2) {
                    throw new IllegalStateException("NEEDS_CONFIRMATION item must contain at least two candidateHeaders.");
                }
                yield new VagueBindingRecoItem(
                        item.targetColumn(),
                        item.ruleType(),
                        item.sourceColumn(),
                        status,
                        null,
                        candidateHeaders,
                        item.reason()
                );
            }
            case MISSING -> {
                if (selectedHeader != null) {
                    throw new IllegalStateException("MISSING item must not contain selectedHeader.");
                }
                if (!candidateHeaders.isEmpty()) {
                    throw new IllegalStateException("MISSING item must not contain candidateHeaders.");
                }
                yield new VagueBindingRecoItem(
                        item.targetColumn(),
                        item.ruleType(),
                        item.sourceColumn(),
                        status,
                        null,
                        List.of(),
                        item.reason()
                );
            }
        };
    }

    /**
     * 生成规则文档侧的绑定覆盖键。
     */
    private String buildExpectedKey(ProcessingRuleItem ruleItem, String sourceColumn) {
        return ruleItem.targetColumn() + "|" + ruleItem.ruleType() + "|" + sourceColumn;
    }

    /**
     * 生成模型返回侧的绑定覆盖键。
     */
    private String buildActualKey(VagueBindingRecoItem item) {
        return item.targetColumn() + "|" + item.ruleType() + "|" + item.sourceColumn();
    }

    /**
     * 将提示词上下文序列化为 JSON，失败时中断当前工作流节点。
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize vague binding recognition payload.", ex);
        }
    }
}
