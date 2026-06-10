package com.example.dataprocess.agent.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Repairs malformed streaming tool call messages so the agent can continue to
 * stream model output while avoiding premature tool execution with incomplete
 * arguments.
 */
public class ToolCallMessageValidator {

    private static final String READ_SKILL_TOOL_NAME = "read_skill";
    private static final String EMPTY_JSON_OBJECT = "{}";
    private static final int ARGUMENTS_SUMMARY_MAX_LEN = 512;
    private static final String INVALID_ARGUMENTS_ERROR_CODE = "INVALID_TOOL_CALL_ARGUMENTS";

    private final ObjectMapper objectMapper;
    private final Set<String> knownToolNames;
    private final Map<String, String> inputSchemasByToolName;

    public ToolCallMessageValidator(ObjectMapper objectMapper, List<ToolCallback> toolCallbacks) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.knownToolNames = knownToolNames(toolCallbacks);
        this.inputSchemasByToolName = inputSchemasByToolName(toolCallbacks);
    }

    public ValidationResult validate(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return ValidationResult.unchanged(messages);
        }

        MessageMergeResult messageMergeResult = mergeAdjacentAssistantMessages(messages);
        List<Message> sourceMessages = messageMergeResult.messages();
        Set<String> respondedToolCallIds = collectRespondedToolCallIds(sourceMessages);
        List<Message> rebuiltMessages = new ArrayList<>(sourceMessages.size());
        List<ValidationDetail> details = new ArrayList<>();
        boolean changed = messageMergeResult.changed();
        int originalToolCallCount = 0;
        int normalizedToolCallCount = 0;
        int syntheticToolResponseCount = 0;

        for (Message message : sourceMessages) {
            if (!(message instanceof AssistantMessage assistantMessage) || !assistantMessage.hasToolCalls()) {
                rebuiltMessages.add(message);
                continue;
            }

            originalToolCallCount += assistantMessage.getToolCalls().size();
            ToolCallRepairResult repairResult = repairToolCalls(assistantMessage.getToolCalls());
            List<AssistantMessage.ToolCall> repairedToolCalls = repairResult.toolCalls();
            normalizedToolCallCount += repairedToolCalls != null ? repairedToolCalls.size() : 0;
            if (repairResult.repaired()) {
                changed = true;
                details.addAll(repairResult.details());
            }

            AssistantProcessingResult assistantResult =
                    normalizeAssistantToolCalls(repairedToolCalls, respondedToolCallIds);
            if (assistantResult.assistantChanged()) {
                changed = true;
            }
            if (!assistantResult.details().isEmpty()) {
                details.addAll(assistantResult.details());
            }

            rebuiltMessages.add(assistantResult.assistantChanged() || repairResult.repaired()
                    ? rebuildAssistantMessage(assistantMessage, assistantResult.toolCalls())
                    : assistantMessage);

            if (!assistantResult.syntheticResponses().isEmpty()) {
                changed = true;
                syntheticToolResponseCount += assistantResult.syntheticResponses().size();
                rebuiltMessages.add(ToolResponseMessage.builder()
                        .responses(assistantResult.syntheticResponses())
                        .metadata(Map.of(
                                "synthetic", true,
                                "error", true,
                                "errorCode", INVALID_ARGUMENTS_ERROR_CODE
                        ))
                        .build());
            }
        }

        if (!changed) {
            return ValidationResult.unchanged(messages);
        }
        return new ValidationResult(
                List.copyOf(rebuiltMessages),
                true,
                originalToolCallCount,
                normalizedToolCallCount,
                syntheticToolResponseCount,
                details.isEmpty() ? List.of() : List.copyOf(details)
        );
    }

    private MessageMergeResult mergeAdjacentAssistantMessages(List<Message> messages) {
        List<Message> mergedMessages = new ArrayList<>(messages.size());
        boolean changed = false;

        for (Message message : messages) {
            if (!(message instanceof AssistantMessage currentAssistant)) {
                mergedMessages.add(message);
                continue;
            }

            if (mergedMessages.isEmpty()) {
                mergedMessages.add(currentAssistant);
                continue;
            }

            Message previousMessage = mergedMessages.get(mergedMessages.size() - 1);
            if (!(previousMessage instanceof AssistantMessage previousAssistant)
                    || !shouldMergeAssistantMessages(previousAssistant, currentAssistant)) {
                mergedMessages.add(currentAssistant);
                continue;
            }

            mergedMessages.set(mergedMessages.size() - 1, mergeAssistantMessages(previousAssistant, currentAssistant));
            changed = true;
        }

        if (!changed) {
            return new MessageMergeResult(messages, false);
        }
        return new MessageMergeResult(List.copyOf(mergedMessages), true);
    }

    private boolean shouldMergeAssistantMessages(AssistantMessage previous, AssistantMessage current) {
        return previous.hasToolCalls() || current.hasToolCalls();
    }

    private AssistantMessage mergeAssistantMessages(AssistantMessage previous, AssistantMessage current) {
        return AssistantMessage.builder()
                .content(mergeText(previous.getText(), current.getText()))
                .properties(mergeMetadata(previous.getMetadata(), current.getMetadata()))
                .toolCalls(mergeToolCalls(previous.getToolCalls(), current.getToolCalls()))
                .media(mergeMedia(previous.getMedia(), current.getMedia()))
                .build();
    }

    private AssistantProcessingResult normalizeAssistantToolCalls(
            List<AssistantMessage.ToolCall> toolCalls,
            Set<String> respondedToolCallIds
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return new AssistantProcessingResult(toolCalls, false, List.of(), List.of());
        }

        List<AssistantMessage.ToolCall> normalizedToolCalls = new ArrayList<>(toolCalls.size());
        List<ToolResponseMessage.ToolResponse> syntheticResponses = new ArrayList<>();
        List<ValidationDetail> details = new ArrayList<>();
        boolean assistantChanged = false;

        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            if (toolCall == null) {
                normalizedToolCalls.add(null);
                continue;
            }

            AssistantMessage.ToolCall preparedToolCall = ensureToolCallId(toolCall);
            if (preparedToolCall != toolCall) {
                assistantChanged = true;
            }

            NormalizedArguments normalizedArguments = validateToolCall(preparedToolCall);
            AssistantMessage.ToolCall normalizedToolCall =
                    normalizeToolCall(preparedToolCall, preparedToolCall.id(), normalizedArguments.arguments());

            if (normalizedArguments.valid()) {
                normalizedToolCalls.add(normalizedToolCall);
                if (normalizedToolCall != preparedToolCall) {
                    assistantChanged = true;
                    details.add(ValidationDetail.normalized(
                            preparedToolCall.id(),
                            preparedToolCall.name(),
                            summarize(preparedToolCall.arguments(), ARGUMENTS_SUMMARY_MAX_LEN)
                    ));
                }
                continue;
            }

            assistantChanged = true;
            normalizedToolCalls.add(normalizedToolCall);
            boolean hasResponse = respondedToolCallIds.contains(preparedToolCall.id());
            boolean addedSyntheticResponse = false;
            if (!hasResponse) {
                syntheticResponses.add(new ToolResponseMessage.ToolResponse(
                        preparedToolCall.id(),
                        preparedToolCall.name(),
                        buildToolArgumentErrorContent(preparedToolCall, normalizedArguments)
                ));
                respondedToolCallIds.add(preparedToolCall.id());
                addedSyntheticResponse = true;
            }
            details.add(ValidationDetail.rejected(
                    preparedToolCall.id(),
                    preparedToolCall.name(),
                    normalizedArguments.status().name(),
                    summarize(preparedToolCall.arguments(), ARGUMENTS_SUMMARY_MAX_LEN),
                    addedSyntheticResponse
            ));
        }

        return new AssistantProcessingResult(
                normalizedToolCalls,
                assistantChanged,
                syntheticResponses,
                details.isEmpty() ? List.of() : List.copyOf(details)
        );
    }

    private NormalizedArguments validateToolCall(AssistantMessage.ToolCall toolCall) {
        if (!StringUtils.hasText(toolCall.name())) {
            return NormalizedArguments.invalid(
                    EMPTY_JSON_OBJECT,
                    NormalizationStatus.MISSING_TOOL_NAME,
                    "Tool name is missing.",
                    null
            );
        }

        if (!knownToolNames.contains(toolCall.name())) {
            return NormalizedArguments.invalid(
                    EMPTY_JSON_OBJECT,
                    NormalizationStatus.UNKNOWN_TOOL_NAME,
                    "Unknown tool name: " + toolCall.name(),
                    null
            );
        }

        return normalizeArguments(
                toolCall.arguments(),
                inputSchemasByToolName.get(toolCall.name()),
                toolCall.name()
        );
    }

    private AssistantMessage.ToolCall ensureToolCallId(AssistantMessage.ToolCall toolCall) {
        if (StringUtils.hasText(toolCall.id())) {
            return toolCall;
        }
        return new AssistantMessage.ToolCall(
                "call-" + UUID.randomUUID(),
                toolCall.type(),
                toolCall.name(),
                toolCall.arguments()
        );
    }

    private ToolCallRepairResult repairToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return ToolCallRepairResult.unchanged(toolCalls);
        }

        List<IndexedToolCall> indexedToolCalls = new ArrayList<>();
        for (int i = 0; i < toolCalls.size(); i++) {
            AssistantMessage.ToolCall toolCall = toolCalls.get(i);
            if (toolCall != null) {
                indexedToolCalls.add(new IndexedToolCall(i, toolCall));
            }
        }

        List<ValidationDetail> details = new ArrayList<>();
        List<OrderedToolCall> normalized = new ArrayList<>();
        Set<Integer> consumed = new LinkedHashSet<>();
        boolean repaired = indexedToolCalls.size() != toolCalls.size();

        Map<String, List<IndexedToolCall>> fragmentsById = groupById(indexedToolCalls);
        for (List<IndexedToolCall> fragments : fragmentsById.values()) {
            if (fragments.size() < 2) {
                continue;
            }
            AssistantMessage.ToolCall mergedToolCall = mergeFragments(fragments);
            if (mergedToolCall == null) {
                continue;
            }
            normalized.add(new OrderedToolCall(minIndex(fragments), mergedToolCall));
            consumed.addAll(indexesOf(fragments));
            repaired = true;
            details.add(ValidationDetail.merged(
                    mergedToolCall.id(),
                    mergedToolCall.name(),
                    fragments.size(),
                    summarize(mergedToolCall.arguments(), ARGUMENTS_SUMMARY_MAX_LEN)
            ));
        }

        HeuristicMergeResult heuristicMergeResult = tryHeuristicMerge(indexedToolCalls, consumed);
        if (heuristicMergeResult.toolCall() != null) {
            normalized.add(new OrderedToolCall(heuristicMergeResult.orderIndex(), heuristicMergeResult.toolCall()));
            consumed.add(heuristicMergeResult.nameFragment().index());
            consumed.add(heuristicMergeResult.argumentsFragment().index());
            repaired = true;
            details.add(ValidationDetail.merged(
                    heuristicMergeResult.toolCall().id(),
                    heuristicMergeResult.toolCall().name(),
                    2,
                    summarize(heuristicMergeResult.toolCall().arguments(), ARGUMENTS_SUMMARY_MAX_LEN)
            ));
        }

        for (IndexedToolCall indexedToolCall : indexedToolCalls) {
            if (consumed.contains(indexedToolCall.index())) {
                continue;
            }
            normalized.add(new OrderedToolCall(indexedToolCall.index(), indexedToolCall.toolCall()));
        }

        normalized.sort(Comparator.comparingInt(OrderedToolCall::index));
        List<AssistantMessage.ToolCall> repairedToolCalls = normalized.stream()
                .map(OrderedToolCall::toolCall)
                .toList();

        if (!repaired) {
            return ToolCallRepairResult.unchanged(toolCalls);
        }
        return new ToolCallRepairResult(
                repairedToolCalls,
                true,
                details.isEmpty() ? List.of() : List.copyOf(details)
        );
    }

    private AssistantMessage.ToolCall mergeFragments(List<IndexedToolCall> fragments) {
        String id = null;
        String type = null;
        String name = null;
        String arguments = null;

        for (IndexedToolCall indexed : fragments) {
            AssistantMessage.ToolCall toolCall = indexed.toolCall();
            id = firstNonBlank(id, toolCall.id());
            type = firstNonBlank(type, toolCall.type());
            name = firstNonBlank(name, toolCall.name());
            arguments = mergeArguments(arguments, toolCall.arguments());
        }

        if (!StringUtils.hasText(id) && !StringUtils.hasText(name) && !StringUtils.hasText(arguments)) {
            return null;
        }
        return new AssistantMessage.ToolCall(id, type, name, arguments);
    }

    private HeuristicMergeResult tryHeuristicMerge(List<IndexedToolCall> indexedToolCalls, Set<Integer> consumed) {
        List<IndexedToolCall> remaining = indexedToolCalls.stream()
                .filter(indexed -> !consumed.contains(indexed.index()))
                .toList();
        if (remaining.size() < 2) {
            return HeuristicMergeResult.empty();
        }

        List<IndexedToolCall> namedFragments = remaining.stream()
                .filter(indexed -> StringUtils.hasText(indexed.toolCall().name()))
                .filter(indexed -> !StringUtils.hasText(indexed.toolCall().arguments())
                        || !looksLikeCompleteJsonObject(indexed.toolCall().arguments()))
                .toList();
        List<IndexedToolCall> argumentFragments = remaining.stream()
                .filter(indexed -> !StringUtils.hasText(indexed.toolCall().name()))
                .filter(indexed -> StringUtils.hasText(indexed.toolCall().arguments()))
                .toList();

        if (namedFragments.size() != 1 || argumentFragments.isEmpty()) {
            return HeuristicMergeResult.empty();
        }

        IndexedToolCall namedFragment = namedFragments.get(0);
        String mergedArguments = null;
        String mergedId = namedFragment.toolCall().id();
        String mergedType = namedFragment.toolCall().type();
        List<IndexedToolCall> consumedFragments = new ArrayList<>();

        for (IndexedToolCall fragment : argumentFragments) {
            mergedArguments = mergeArguments(mergedArguments, fragment.toolCall().arguments());
            mergedId = firstNonBlank(mergedId, fragment.toolCall().id());
            mergedType = firstNonBlank(mergedType, fragment.toolCall().type());
            consumedFragments.add(fragment);
            if (looksLikeCompleteJsonObject(mergedArguments)) {
                break;
            }
        }

        if (!looksLikeCompleteJsonObject(mergedArguments)) {
            return HeuristicMergeResult.empty();
        }

        AssistantMessage.ToolCall mergedToolCall = new AssistantMessage.ToolCall(
                mergedId,
                mergedType,
                namedFragment.toolCall().name(),
                mergedArguments
        );
        return new HeuristicMergeResult(
                mergedToolCall,
                namedFragment,
                consumedFragments.get(consumedFragments.size() - 1),
                Math.min(namedFragment.index(),
                        consumedFragments.stream().mapToInt(IndexedToolCall::index).min().orElse(namedFragment.index()))
        );
    }

    private AssistantMessage.ToolCall normalizeToolCall(
            AssistantMessage.ToolCall toolCall,
            String normalizedId,
            String normalizedArguments
    ) {
        if (Objects.equals(toolCall.id(), normalizedId) && Objects.equals(toolCall.arguments(), normalizedArguments)) {
            return toolCall;
        }
        return new AssistantMessage.ToolCall(
                normalizedId,
                toolCall.type(),
                toolCall.name(),
                normalizedArguments
        );
    }

    private AssistantMessage rebuildAssistantMessage(
            AssistantMessage source,
            List<AssistantMessage.ToolCall> toolCalls
    ) {
        return AssistantMessage.builder()
                .content(source.getText())
                .properties(source.getMetadata())
                .toolCalls(toolCalls)
                .media(source.getMedia())
                .build();
    }

    private String buildToolArgumentErrorContent(
            AssistantMessage.ToolCall toolCall,
            NormalizedArguments invalidArguments
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorCode", INVALID_ARGUMENTS_ERROR_CODE);
        payload.put("toolName", toolCall.name());
        payload.put("reason", invalidArguments.status().name());
        payload.put("message", invalidArguments.message());
        payload.put("argumentsSummary", summarize(toolCall.arguments(), ARGUMENTS_SUMMARY_MAX_LEN));
        payload.put("retryable", true);
        payload.put("instruction",
                "Call the tool again with complete JSON object arguments. For a no-argument tool, use {}.");
        if (StringUtils.hasText(invalidArguments.parserMessage())) {
            payload.put("parserMessage", summarize(invalidArguments.parserMessage(), ARGUMENTS_SUMMARY_MAX_LEN));
        }
        try {
            return objectMapper.writeValueAsString(payload);
        }
        catch (JsonProcessingException ignored) {
            return "[" + INVALID_ARGUMENTS_ERROR_CODE + "] " + invalidArguments.message();
        }
    }

    private NormalizedArguments normalizeArguments(String arguments, String inputSchema, String toolName) {
        JsonNode schemaNode = parseJson(inputSchema);
        Set<String> requiredFields = requiredFields(schemaNode);
        boolean requiresArguments = READ_SKILL_TOOL_NAME.equals(toolName) || !requiredFields.isEmpty();

        if (!StringUtils.hasText(arguments)) {
            if (!requiresArguments) {
                return NormalizedArguments.valid(EMPTY_JSON_OBJECT, NormalizationStatus.NORMALIZED_EMPTY_ARGUMENTS);
            }
            return NormalizedArguments.invalid(
                    EMPTY_JSON_OBJECT,
                    NormalizationStatus.EMPTY_ARGUMENTS,
                    "Tool arguments are empty.",
                    null
            );
        }

        ParsedJson argumentsJson = parseJsonWithError(arguments);
        if (argumentsJson.node() == null) {
            return NormalizedArguments.invalid(
                    EMPTY_JSON_OBJECT,
                    NormalizationStatus.INVALID_JSON,
                    "Tool arguments are not valid JSON.",
                    argumentsJson.errorMessage()
            );
        }

        if (!argumentsJson.node().isObject()) {
            return NormalizedArguments.invalid(
                    EMPTY_JSON_OBJECT,
                    NormalizationStatus.NOT_JSON_OBJECT,
                    "Tool arguments must be a JSON object.",
                    null
            );
        }

        Set<String> missingFields = missingRequiredFields(argumentsJson.node(), requiredFields, toolName);
        if (!missingFields.isEmpty()) {
            return NormalizedArguments.invalid(
                    EMPTY_JSON_OBJECT,
                    NormalizationStatus.MISSING_REQUIRED_FIELDS,
                    "Missing required fields: " + String.join(", ", missingFields),
                    null
            );
        }

        return NormalizedArguments.valid(arguments, NormalizationStatus.VALID);
    }

    private Set<String> missingRequiredFields(JsonNode argumentsNode, Set<String> requiredFields, String toolName) {
        Set<String> missing = new LinkedHashSet<>();
        for (String field : requiredFields) {
            JsonNode fieldNode = argumentsNode.get(field);
            if (fieldNode == null || fieldNode.isNull() || (fieldNode.isTextual() && fieldNode.asText().isBlank())) {
                missing.add(field);
            }
        }

        if (READ_SKILL_TOOL_NAME.equals(toolName)
                && !hasTextNode(argumentsNode.get("skillName"))
                && !hasTextNode(argumentsNode.get("skill_name"))) {
            missing.add("skillName");
        }

        return missing;
    }

    private Set<String> requiredFields(JsonNode schemaNode) {
        if (schemaNode == null || !schemaNode.isObject()) {
            return Set.of();
        }
        JsonNode requiredNode = schemaNode.get("required");
        if (requiredNode == null || !requiredNode.isArray()) {
            return Set.of();
        }
        Set<String> requiredFields = new LinkedHashSet<>();
        for (JsonNode item : requiredNode) {
            if (item != null && item.isTextual() && !item.asText().isBlank()) {
                requiredFields.add(item.asText());
            }
        }
        return requiredFields;
    }

    private Set<String> collectRespondedToolCallIds(List<Message> messages) {
        Set<String> respondedIds = new LinkedHashSet<>();
        for (Message message : messages) {
            if (!(message instanceof ToolResponseMessage toolResponseMessage)) {
                continue;
            }
            for (ToolResponseMessage.ToolResponse response : toolResponseMessage.getResponses()) {
                if (response != null && StringUtils.hasText(response.id())) {
                    respondedIds.add(response.id());
                }
            }
        }
        return respondedIds;
    }

    private Set<String> knownToolNames(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            return Set.of();
        }
        Set<String> toolNames = new LinkedHashSet<>();
        for (ToolCallback toolCallback : toolCallbacks) {
            if (toolCallback == null) {
                continue;
            }
            ToolDefinition toolDefinition = toolCallback.getToolDefinition();
            if (toolDefinition != null && StringUtils.hasText(toolDefinition.name())) {
                toolNames.add(toolDefinition.name());
            }
        }
        return Set.copyOf(toolNames);
    }

    private Map<String, String> inputSchemasByToolName(List<ToolCallback> toolCallbacks) {
        if (toolCallbacks == null || toolCallbacks.isEmpty()) {
            return Map.of();
        }
        Map<String, String> inputSchemas = new LinkedHashMap<>();
        for (ToolCallback toolCallback : toolCallbacks) {
            if (toolCallback == null) {
                continue;
            }
            ToolDefinition toolDefinition = toolCallback.getToolDefinition();
            if (toolDefinition == null
                    || !StringUtils.hasText(toolDefinition.name())
                    || !StringUtils.hasText(toolDefinition.inputSchema())) {
                continue;
            }
            inputSchemas.putIfAbsent(toolDefinition.name(), toolDefinition.inputSchema());
        }
        return Map.copyOf(inputSchemas);
    }

    private Map<String, List<IndexedToolCall>> groupById(List<IndexedToolCall> indexedToolCalls) {
        Map<String, List<IndexedToolCall>> grouped = new LinkedHashMap<>();
        for (IndexedToolCall indexedToolCall : indexedToolCalls) {
            if (!StringUtils.hasText(indexedToolCall.toolCall().id())) {
                continue;
            }
            grouped.computeIfAbsent(indexedToolCall.toolCall().id(), ignored -> new ArrayList<>())
                    .add(indexedToolCall);
        }
        return grouped;
    }

    private Set<Integer> indexesOf(List<IndexedToolCall> fragments) {
        Set<Integer> indexes = new LinkedHashSet<>();
        for (IndexedToolCall fragment : fragments) {
            indexes.add(fragment.index());
        }
        return indexes;
    }

    private int minIndex(List<IndexedToolCall> fragments) {
        return fragments.stream().mapToInt(IndexedToolCall::index).min().orElse(Integer.MAX_VALUE);
    }

    private ParsedJson parseJsonWithError(String text) {
        if (!StringUtils.hasText(text)) {
            return new ParsedJson(null, null);
        }
        try {
            return new ParsedJson(objectMapper.readTree(text), null);
        }
        catch (Exception ex) {
            return new ParsedJson(null, ex.getMessage());
        }
    }

    private JsonNode parseJson(String text) {
        return parseJsonWithError(text).node();
    }

    private String mergeArguments(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return second;
        }
        if (!StringUtils.hasText(second)) {
            return first;
        }
        return first + second;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String mergeText(String first, String second) {
        if (!StringUtils.hasText(first)) {
            return second;
        }
        if (!StringUtils.hasText(second)) {
            return first;
        }
        return first + second;
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> first, Map<String, Object> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return Map.of();
        }
        Map<String, Object> merged = new HashMap<>();
        if (first != null && !first.isEmpty()) {
            merged.putAll(first);
        }
        if (second != null && !second.isEmpty()) {
            merged.putAll(second);
        }
        return merged;
    }

    private List<AssistantMessage.ToolCall> mergeToolCalls(
            List<AssistantMessage.ToolCall> first,
            List<AssistantMessage.ToolCall> second
    ) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return List.of();
        }
        List<AssistantMessage.ToolCall> merged = new ArrayList<>();
        if (first != null && !first.isEmpty()) {
            merged.addAll(first);
        }
        if (second != null && !second.isEmpty()) {
            merged.addAll(second);
        }
        return merged;
    }

    private List<Media> mergeMedia(List<Media> first, List<Media> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return List.of();
        }
        List<Media> merged = new ArrayList<>();
        if (first != null && !first.isEmpty()) {
            merged.addAll(first);
        }
        if (second != null && !second.isEmpty()) {
            merged.addAll(second);
        }
        return merged;
    }

    private boolean looksLikeCompleteJsonObject(String text) {
        JsonNode node = parseJson(text);
        return node != null && node.isObject();
    }

    private boolean hasTextNode(JsonNode node) {
        return node != null && !node.isNull() && StringUtils.hasText(node.asText());
    }

    public static String summarize(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen) + "...";
    }

    public record ValidationResult(
            List<Message> messages,
            boolean changed,
            int originalToolCallCount,
            int normalizedToolCallCount,
            int syntheticToolResponseCount,
            List<ValidationDetail> details
    ) {
        private static ValidationResult unchanged(List<Message> messages) {
            return new ValidationResult(messages, false, 0, 0, 0, List.of());
        }
    }

    public record ValidationDetail(
            String toolCallId,
            String toolName,
            String action,
            String errorCode,
            int originalCount,
            String argumentsSummary,
            boolean syntheticToolResponseAdded
    ) {
        private static ValidationDetail merged(
                String toolCallId,
                String toolName,
                int originalCount,
                String argumentsSummary
        ) {
            return new ValidationDetail(
                    toolCallId,
                    toolName,
                    "MERGED_DUPLICATE_CHUNKS",
                    null,
                    originalCount,
                    argumentsSummary,
                    false
            );
        }

        private static ValidationDetail normalized(
                String toolCallId,
                String toolName,
                String argumentsSummary
        ) {
            return new ValidationDetail(
                    toolCallId,
                    toolName,
                    "NORMALIZED_EMPTY_ARGUMENTS",
                    null,
                    1,
                    argumentsSummary,
                    false
            );
        }

        private static ValidationDetail rejected(
                String toolCallId,
                String toolName,
                String errorCode,
                String argumentsSummary,
                boolean syntheticToolResponseAdded
        ) {
            return new ValidationDetail(
                    toolCallId,
                    toolName,
                    "REJECTED_INVALID_ARGUMENTS",
                    errorCode,
                    1,
                    argumentsSummary,
                    syntheticToolResponseAdded
            );
        }
    }

    private record AssistantProcessingResult(
            List<AssistantMessage.ToolCall> toolCalls,
            boolean assistantChanged,
            List<ToolResponseMessage.ToolResponse> syntheticResponses,
            List<ValidationDetail> details
    ) {
    }

    private record ToolCallRepairResult(
            List<AssistantMessage.ToolCall> toolCalls,
            boolean repaired,
            List<ValidationDetail> details
    ) {
        private static ToolCallRepairResult unchanged(List<AssistantMessage.ToolCall> toolCalls) {
            return new ToolCallRepairResult(toolCalls, false, List.of());
        }
    }

    private enum NormalizationStatus {
        VALID,
        NORMALIZED_EMPTY_ARGUMENTS,
        EMPTY_ARGUMENTS,
        INVALID_JSON,
        NOT_JSON_OBJECT,
        MISSING_REQUIRED_FIELDS,
        MISSING_TOOL_NAME,
        UNKNOWN_TOOL_NAME
    }

    private record NormalizedArguments(
            boolean valid,
            String arguments,
            NormalizationStatus status,
            String message,
            String parserMessage
    ) {
        private static NormalizedArguments valid(String arguments, NormalizationStatus status) {
            return new NormalizedArguments(true, arguments, status, "OK", null);
        }

        private static NormalizedArguments invalid(
                String fallbackArguments,
                NormalizationStatus status,
                String message,
                String parserMessage
        ) {
            return new NormalizedArguments(false, fallbackArguments, status, message, parserMessage);
        }
    }

    private record IndexedToolCall(int index, AssistantMessage.ToolCall toolCall) {
    }

    private record OrderedToolCall(int index, AssistantMessage.ToolCall toolCall) {
    }

    private record ParsedJson(JsonNode node, String errorMessage) {
    }

    private record MessageMergeResult(List<Message> messages, boolean changed) {
    }

    private record HeuristicMergeResult(
            AssistantMessage.ToolCall toolCall,
            IndexedToolCall nameFragment,
            IndexedToolCall argumentsFragment,
            int orderIndex
    ) {
        private static HeuristicMergeResult empty() {
            return new HeuristicMergeResult(null, null, null, Integer.MAX_VALUE);
        }
    }
}
