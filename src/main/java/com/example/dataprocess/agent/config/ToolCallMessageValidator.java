package com.example.dataprocess.agent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.content.Media;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Repairs fragmented or invalid tool calls in assistant messages before they are
 * executed by the agent tool node or replayed into a subsequent model request.
 */
public class ToolCallMessageValidator {

    private static final String READ_SKILL_TOOL_NAME = "read_skill";

    private final ObjectMapper objectMapper;
    private final Map<String, ToolCallback> toolCallbacksByName;

    public ToolCallMessageValidator(ObjectMapper objectMapper, List<ToolCallback> toolCallbacks) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.toolCallbacksByName = indexToolCallbacks(toolCallbacks);
    }

    public ValidationResult validate(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ValidationResult(false, messages, List.of(), 0, 0, 0, false);
        }

        List<Message> repairedMessages = new ArrayList<>(messages.size());
        List<ValidationDetail> details = new ArrayList<>();
        boolean changed = false;
        boolean requestModelRetry = false;
        int originalToolCallCount = 0;
        int normalizedToolCallCount = 0;
        int lastIndex = messages.size() - 1;

        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof AssistantMessage assistantMessage && assistantMessage.hasToolCalls()) {
                RepairAssistantResult repairResult = repairAssistantMessage(assistantMessage, i == lastIndex);
                originalToolCallCount += repairResult.originalToolCallCount();
                normalizedToolCallCount += repairResult.normalizedToolCallCount();
                details.addAll(repairResult.details());
                changed = changed || repairResult.changed();
                requestModelRetry = requestModelRetry || repairResult.requestModelRetry();
                if (repairResult.message() != null) {
                    repairedMessages.add(repairResult.message());
                }
            }
            else {
                repairedMessages.add(message);
            }
        }

        return new ValidationResult(
                changed,
                changed ? List.copyOf(repairedMessages) : messages,
                details.isEmpty() ? List.of() : List.copyOf(details),
                originalToolCallCount,
                normalizedToolCallCount,
                0,
                requestModelRetry
        );
    }

    private RepairAssistantResult repairAssistantMessage(AssistantMessage message, boolean lastMessage) {
        ToolCallRepairResult repairResult = normalizeToolCalls(message.getToolCalls());
        if (!repairResult.changed()) {
            return new RepairAssistantResult(
                    message,
                    false,
                    List.of(),
                    repairResult.originalToolCallCount(),
                    repairResult.normalizedToolCallCount(),
                    false
            );
        }

        AssistantMessage repairedMessage = null;
        if (!repairResult.toolCalls().isEmpty() || hasVisibleContent(message) || hasMedia(message)) {
            repairedMessage = AssistantMessage.builder()
                    .content(message.getText() == null ? "" : message.getText())
                    .properties(message.getMetadata())
                    .toolCalls(repairResult.toolCalls())
                    .media(message.getMedia())
                    .build();
        }

        boolean requestModelRetry = lastMessage
                && repairResult.hadInvalidFragments()
                && (repairedMessage == null || !repairedMessage.hasToolCalls());

        return new RepairAssistantResult(
                repairedMessage,
                true,
                repairResult.details(),
                repairResult.originalToolCallCount(),
                repairResult.normalizedToolCallCount(),
                requestModelRetry
        );
    }

    private ToolCallRepairResult normalizeToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return new ToolCallRepairResult(List.of(), false, List.of(), 0, 0, false);
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
        boolean changed = indexedToolCalls.size() != toolCalls.size();
        boolean hadInvalidFragments = indexedToolCalls.size() != toolCalls.size();

        Map<String, List<IndexedToolCall>> fragmentsById = groupById(indexedToolCalls);
        for (List<IndexedToolCall> fragments : fragmentsById.values()) {
            if (fragments.size() < 2) {
                continue;
            }
            MergeResult merged = mergeFragments(fragments);
            if (merged.toolCall() != null && isCompleteToolCall(merged.toolCall())) {
                normalized.add(new OrderedToolCall(minIndex(fragments), merged.toolCall()));
                consumed.addAll(indexesOf(fragments));
                changed = true;
                details.add(new ValidationDetail(
                        merged.toolCall().id(),
                        merged.toolCall().name(),
                        RepairAction.MERGE_FRAGMENTED_TOOL_CALLS,
                        "FRAGMENTED_TOOL_CALL",
                        fragments.size(),
                        summarizeArguments(merged.toolCall().arguments()),
                        false
                ));
            }
        }

        List<IndexedToolCall> remaining = indexedToolCalls.stream()
                .filter(indexed -> !consumed.contains(indexed.index()))
                .toList();

        HeuristicMergeResult heuristicMergeResult = tryHeuristicMerge(remaining);
        if (heuristicMergeResult.toolCall() != null) {
            normalized.add(new OrderedToolCall(heuristicMergeResult.orderIndex(), heuristicMergeResult.toolCall()));
            consumed.add(heuristicMergeResult.nameFragment().index());
            consumed.add(heuristicMergeResult.argumentsFragment().index());
            changed = true;
            details.add(new ValidationDetail(
                    heuristicMergeResult.toolCall().id(),
                    heuristicMergeResult.toolCall().name(),
                    RepairAction.MERGE_FRAGMENTED_TOOL_CALLS,
                    "FRAGMENTED_TOOL_CALL",
                    2,
                    summarizeArguments(heuristicMergeResult.toolCall().arguments()),
                    false
            ));
        }

        for (IndexedToolCall indexedToolCall : indexedToolCalls) {
            if (consumed.contains(indexedToolCall.index())) {
                continue;
            }

            AssistantMessage.ToolCall toolCall = indexedToolCall.toolCall();
            if (isCompleteToolCall(toolCall)) {
                normalized.add(new OrderedToolCall(indexedToolCall.index(), toolCall));
                continue;
            }

            changed = true;
            hadInvalidFragments = true;
            details.add(new ValidationDetail(
                    safeId(toolCall),
                    safeName(toolCall),
                    RepairAction.REMOVE_INVALID_TOOL_CALL,
                    classifyErrorCode(toolCall),
                    1,
                    summarizeArguments(toolCall.arguments()),
                    false
            ));
        }

        normalized.sort(Comparator.comparingInt(OrderedToolCall::index));
        List<AssistantMessage.ToolCall> normalizedToolCalls = normalized.stream()
                .map(OrderedToolCall::toolCall)
                .toList();

        return new ToolCallRepairResult(
                normalizedToolCalls,
                changed,
                details.isEmpty() ? List.of() : List.copyOf(details),
                toolCalls.size(),
                normalizedToolCalls.size(),
                hadInvalidFragments
        );
    }

    private HeuristicMergeResult tryHeuristicMerge(List<IndexedToolCall> indexedToolCalls) {
        if (indexedToolCalls == null || indexedToolCalls.size() < 2) {
            return HeuristicMergeResult.empty();
        }

        List<IndexedToolCall> namedFragments = indexedToolCalls.stream()
                .filter(indexed -> hasText(indexed.toolCall().name()))
                .filter(indexed -> !hasValidArguments(indexed.toolCall()))
                .toList();
        List<IndexedToolCall> argumentFragments = indexedToolCalls.stream()
                .filter(indexed -> !hasText(indexed.toolCall().name()))
                .filter(indexed -> hasValidArguments(indexed.toolCall()))
                .toList();

        if (namedFragments.size() != 1 || argumentFragments.size() != 1) {
            return HeuristicMergeResult.empty();
        }

        IndexedToolCall namedFragment = namedFragments.get(0);
        IndexedToolCall argumentsFragment = argumentFragments.get(0);
        AssistantMessage.ToolCall merged = new AssistantMessage.ToolCall(
                preferredValue(namedFragment.toolCall().id(), argumentsFragment.toolCall().id()),
                preferredValue(namedFragment.toolCall().type(), argumentsFragment.toolCall().type()),
                namedFragment.toolCall().name(),
                argumentsFragment.toolCall().arguments()
        );
        if (!isCompleteToolCall(merged)) {
            return HeuristicMergeResult.empty();
        }

        return new HeuristicMergeResult(
                merged,
                namedFragment,
                argumentsFragment,
                Math.min(namedFragment.index(), argumentsFragment.index())
        );
    }

    private MergeResult mergeFragments(List<IndexedToolCall> fragments) {
        String id = null;
        String type = null;
        String name = null;
        String arguments = null;
        boolean argumentsMerged = false;

        for (IndexedToolCall indexed : fragments) {
            AssistantMessage.ToolCall toolCall = indexed.toolCall();
            id = preferredValue(id, toolCall.id());
            type = preferredValue(type, toolCall.type());
            name = preferredValue(name, toolCall.name());
            if (hasText(toolCall.arguments())) {
                if (!hasText(arguments)) {
                    arguments = toolCall.arguments();
                }
                else if (!Objects.equals(arguments, toolCall.arguments())) {
                    arguments = arguments + toolCall.arguments();
                    argumentsMerged = true;
                }
            }
        }

        AssistantMessage.ToolCall merged = new AssistantMessage.ToolCall(id, type, name, arguments);
        if (!argumentsMerged && !isCompleteToolCall(merged)) {
            return MergeResult.empty();
        }
        return new MergeResult(merged);
    }

    private boolean isCompleteToolCall(AssistantMessage.ToolCall toolCall) {
        if (toolCall == null) {
            return false;
        }
        if (!hasText(toolCall.id()) || !hasText(toolCall.name()) || !isKnownTool(toolCall.name())) {
            return false;
        }
        JsonNode argumentsNode = parseArguments(toolCall.arguments());
        if (argumentsNode == null || !argumentsNode.isObject()) {
            return false;
        }
        return hasRequiredArguments(toolCall.name(), argumentsNode);
    }

    private boolean hasRequiredArguments(String toolName, JsonNode argumentsNode) {
        if (!READ_SKILL_TOOL_NAME.equals(toolName)) {
            return true;
        }
        return hasText(nodeText(argumentsNode.get("skillName")))
                || hasText(nodeText(argumentsNode.get("skill_name")));
    }

    private boolean hasValidArguments(AssistantMessage.ToolCall toolCall) {
        if (toolCall == null) {
            return false;
        }
        JsonNode argumentsNode = parseArguments(toolCall.arguments());
        return argumentsNode != null && argumentsNode.isObject();
    }

    private JsonNode parseArguments(String arguments) {
        if (!hasText(arguments)) {
            return null;
        }
        try {
            return objectMapper.readTree(arguments);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    private boolean isKnownTool(String toolName) {
        return hasText(toolName) && toolCallbacksByName.containsKey(toolName);
    }

    private Map<String, List<IndexedToolCall>> groupById(List<IndexedToolCall> indexedToolCalls) {
        Map<String, List<IndexedToolCall>> grouped = new LinkedHashMap<>();
        for (IndexedToolCall indexedToolCall : indexedToolCalls) {
            String id = indexedToolCall.toolCall().id();
            if (!hasText(id)) {
                continue;
            }
            grouped.computeIfAbsent(id, ignored -> new ArrayList<>()).add(indexedToolCall);
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

    private String classifyErrorCode(AssistantMessage.ToolCall toolCall) {
        if (!hasText(toolCall.name())) {
            return "MISSING_TOOL_NAME";
        }
        if (!isKnownTool(toolCall.name())) {
            return "UNKNOWN_TOOL_NAME";
        }
        JsonNode argumentsNode = parseArguments(toolCall.arguments());
        if (argumentsNode == null) {
            return hasText(toolCall.arguments()) ? "INVALID_TOOL_ARGUMENTS_JSON" : "MISSING_TOOL_ARGUMENTS";
        }
        if (!argumentsNode.isObject()) {
            return "INVALID_TOOL_ARGUMENTS_JSON";
        }
        if (!hasRequiredArguments(toolCall.name(), argumentsNode)) {
            return "MISSING_REQUIRED_ARGUMENT";
        }
        return "INVALID_TOOL_CALL";
    }

    private String summarizeArguments(String arguments) {
        if (arguments == null) {
            return "";
        }
        String compact = arguments.replaceAll("\\s+", " ").trim();
        return compact.length() <= 120 ? compact : compact.substring(0, 117) + "...";
    }

    private Map<String, ToolCallback> indexToolCallbacks(List<ToolCallback> toolCallbacks) {
        Map<String, ToolCallback> indexed = new LinkedHashMap<>();
        if (toolCallbacks == null) {
            return indexed;
        }
        for (ToolCallback toolCallback : toolCallbacks) {
            if (toolCallback == null || toolCallback.getToolDefinition() == null) {
                continue;
            }
            indexed.putIfAbsent(toolCallback.getToolDefinition().name(), toolCallback);
        }
        return indexed;
    }

    private String preferredValue(String first, String second) {
        return hasText(first) ? first : second;
    }

    private boolean hasVisibleContent(AssistantMessage message) {
        return message.getText() != null && !message.getText().isBlank();
    }

    private boolean hasMedia(AssistantMessage message) {
        List<Media> media = message.getMedia();
        return media != null && !media.isEmpty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nodeText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private String safeId(AssistantMessage.ToolCall toolCall) {
        return toolCall == null || toolCall.id() == null ? "" : toolCall.id();
    }

    private String safeName(AssistantMessage.ToolCall toolCall) {
        return toolCall == null || toolCall.name() == null ? "" : toolCall.name();
    }

    public record ValidationResult(
            boolean changed,
            List<Message> messages,
            List<ValidationDetail> details,
            int originalToolCallCount,
            int normalizedToolCallCount,
            int syntheticToolResponseCount,
            boolean requestModelRetry
    ) {
    }

    public record ValidationDetail(
            String toolCallId,
            String toolName,
            RepairAction action,
            String errorCode,
            int originalCount,
            String argumentsSummary,
            boolean syntheticToolResponseAdded
    ) {
    }

    public enum RepairAction {
        KEEP,
        REMOVE_INVALID_TOOL_CALL,
        MERGE_FRAGMENTED_TOOL_CALLS,
        REPLACE_TOOL_CALLS
    }

    private record RepairAssistantResult(
            AssistantMessage message,
            boolean changed,
            List<ValidationDetail> details,
            int originalToolCallCount,
            int normalizedToolCallCount,
            boolean requestModelRetry
    ) {
    }

    private record ToolCallRepairResult(
            List<AssistantMessage.ToolCall> toolCalls,
            boolean changed,
            List<ValidationDetail> details,
            int originalToolCallCount,
            int normalizedToolCallCount,
            boolean hadInvalidFragments
    ) {
    }

    private record IndexedToolCall(int index, AssistantMessage.ToolCall toolCall) {
    }

    private record OrderedToolCall(int index, AssistantMessage.ToolCall toolCall) {
    }

    private record MergeResult(AssistantMessage.ToolCall toolCall) {
        private static MergeResult empty() {
            return new MergeResult(null);
        }
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
