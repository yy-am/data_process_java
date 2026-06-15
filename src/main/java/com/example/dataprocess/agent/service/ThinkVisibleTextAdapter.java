package com.example.dataprocess.agent.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapts streamed model text for clients that only render text inside <think>
 * tags.
 *
 * <p>The adapter is intentionally presentation-only: it should be applied when
 * building outbound stream events, not to the agent's internal message history.
 */
@Component
public class ThinkVisibleTextAdapter {

    private static final String OPEN_TAG = "<think>";
    private static final String CLOSE_TAG = "</think>";
    private static final String ACTION_PREFIX = "[\u884c\u52a8]\n";
    private static final String KEY_SEPARATOR = "\u0000";

    private final Map<String, ParseState> states = new ConcurrentHashMap<>();

    /**
     * Converts one streamed model text fragment into one or more complete
     * <think> blocks that the existing frontend can render.
     *
     * <p>Fragments outside the original <think> block are treated as visible
     * action text and wrapped in a synthetic <think> block. Parser state is kept
     * per task and node so split tags, such as {@code <thi} + {@code nk>}, can
     * be recognized across chunks.
     *
     * @param taskId task-scoped stream id
     * @param node graph node that produced the text
     * @param text raw streamed model text fragment
     * @return frontend-compatible text, or the original null/empty value
     */
    public String adapt(String taskId, String node, String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        ParseState state = states.computeIfAbsent(stateKey(taskId, node), ignored -> new ParseState());
        synchronized (state) {
            return adaptWithState(state, text);
        }
    }

    /**
     * Removes parser state for all nodes of a completed task.
     *
     * <p>Call this when the agent stream finishes, errors, or is cancelled to
     * prevent an unfinished tag prefix from leaking into a later run with the
     * same task id.
     *
     * @param taskId task id whose parser states should be discarded
     */
    public void clearTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        String prefix = taskId + KEY_SEPARATOR;
        states.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Applies the tag-aware state machine to one chunk after prepending any tag
     * prefix held from the previous chunk.
     */
    private String adaptWithState(ParseState state, String text) {
        String data = state.pendingTagPrefix + text;
        state.pendingTagPrefix = "";

        int holdLength = partialTagSuffixLength(data);
        String processable = data.substring(0, data.length() - holdLength);
        if (holdLength > 0) {
            state.pendingTagPrefix = data.substring(data.length() - holdLength);
        }

        StringBuilder output = new StringBuilder();
        int index = 0;
        while (index < processable.length()) {
            if (processable.startsWith(OPEN_TAG, index)) {
                state.insideThink = true;
                state.actionPrefixEmitted = false;
                index += OPEN_TAG.length();
                continue;
            }
            if (processable.startsWith(CLOSE_TAG, index)) {
                state.insideThink = false;
                state.actionPrefixEmitted = false;
                index += CLOSE_TAG.length();
                continue;
            }

            int nextTagIndex = nextTagIndex(processable, index);
            String content = processable.substring(index, nextTagIndex);
            appendVisibleBlock(output, state, content);
            index = nextTagIndex;
        }
        return output.toString();
    }

    /**
     * Emits a complete <think> block for content that should be visible to the
     * current frontend.
     */
    private void appendVisibleBlock(StringBuilder output, ParseState state, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        String visibleContent = content;
        if (!state.insideThink && containsNonWhitespace(content) && !state.actionPrefixEmitted) {
            visibleContent = ACTION_PREFIX + content;
            state.actionPrefixEmitted = true;
        }

        output.append(OPEN_TAG).append(visibleContent).append(CLOSE_TAG);
    }

    /**
     * Finds the next complete think tag boundary from the supplied offset.
     */
    private int nextTagIndex(String value, int fromIndex) {
        int openIndex = value.indexOf(OPEN_TAG, fromIndex);
        int closeIndex = value.indexOf(CLOSE_TAG, fromIndex);
        if (openIndex < 0) {
            return closeIndex < 0 ? value.length() : closeIndex;
        }
        if (closeIndex < 0) {
            return openIndex;
        }
        return Math.min(openIndex, closeIndex);
    }

    private int partialTagSuffixLength(String value) {
        return Math.max(partialTagSuffixLength(value, OPEN_TAG), partialTagSuffixLength(value, CLOSE_TAG));
    }

    /**
     * Returns the length of a suffix that might become a full tag after the
     * next chunk arrives.
     */
    private int partialTagSuffixLength(String value, String tag) {
        int maxLength = Math.min(value.length(), tag.length() - 1);
        for (int length = maxLength; length > 0; length--) {
            if (value.endsWith(tag.substring(0, length))) {
                return length;
            }
        }
        return 0;
    }

    /**
     * Checks whether a fragment contains text worth marking as an action.
     */
    private boolean containsNonWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the state map key. The separator is a non-printing character so
     * task ids and node names cannot accidentally collide.
     */
    private String stateKey(String taskId, String node) {
        return safe(taskId) + KEY_SEPARATOR + safe(node);
    }

    /**
     * Normalizes nullable key parts before composing the parser state key.
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Mutable parser state for one task/node text stream.
     */
    private static final class ParseState {

        private boolean insideThink;

        private boolean actionPrefixEmitted;

        private String pendingTagPrefix = "";

    }
}
