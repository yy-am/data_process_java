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
 * It opens one visible <think> block for the whole task and keeps that block
 * open across all thought/action chunks until the final response is emitted.
 */
@Component
public class ThinkVisibleTextAdapter {

    private static final String OPEN_TAG = "<think>";
    private static final String CLOSE_TAG = "</think>";
    private static final String ACTION_PREFIX = "[\u884c\u52a8]\n";
    private static final String KEY_SEPARATOR = "\u0000";

    private final Map<String, ParseState> states = new ConcurrentHashMap<>();

    /**
     * Converts one streamed model text fragment into frontend-compatible text.
     *
     * <p>Fragments outside the original <think> block are treated as visible
     * action text and prefixed with an action marker. Parser state is kept per
     * task so split tags, such as {@code <thi} + {@code nk>}, can be recognized
     * across chunks and nodes while the frontend sees a single visible block.
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

        ParseState state = states.computeIfAbsent(stateKey(taskId), ignored -> new ParseState());
        synchronized (state) {
            return adaptWithState(state, text);
        }
    }

    /**
     * Closes the task-level visible block, if it has been opened.
     *
     * <p>This method is kept for call sites that close a stream explicitly, but
     * the normal path should close only once before the final response.
     *
     * @param taskId task-scoped stream id
     * @param node graph node that produced the text
     * @return a closing tag when the visible block was open, otherwise an empty string
     */
    public String closeOpenSegment(String taskId, String node) {
        ParseState state = states.get(stateKey(taskId));
        if (state == null) {
            return "";
        }
        synchronized (state) {
            return closeVisibleBlock(state);
        }
    }

    /**
     * Closes the currently open visible block for a task.
     *
     * <p>This is used before the final response so the frontend receives a
     * single complete <think> block for the whole agent run.
     *
     * @param taskId task id whose visible block should be closed
     * @return a closing tag when the visible block was open, otherwise an empty string
     */
    public String closeTaskSegments(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return "";
        }
        ParseState state = states.get(stateKey(taskId));
        if (state == null) {
            return "";
        }
        synchronized (state) {
            return closeVisibleBlock(state);
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
        states.remove(stateKey(taskId));
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
                output.append(openVisibleBlock(state));
                state.insideModelThink = true;
                state.actionPrefixEmitted = false;
                index += OPEN_TAG.length();
                continue;
            }
            if (processable.startsWith(CLOSE_TAG, index)) {
                state.insideModelThink = false;
                state.actionPrefixEmitted = false;
                index += CLOSE_TAG.length();
                continue;
            }

            int nextTagIndex = nextTagIndex(processable, index);
            String content = processable.substring(index, nextTagIndex);
            appendVisibleContent(output, state, content);
            index = nextTagIndex;
        }
        return output.toString();
    }

    /**
     * Emits content into the task-level visible block. A new action marker is
     * emitted only when action text first appears after a model think boundary.
     */
    private void appendVisibleContent(StringBuilder output, ParseState state, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }

        if (!state.visibleBlockOpen && !containsNonWhitespace(content)) {
            return;
        }

        output.append(openVisibleBlock(state));
        if (state.insideModelThink) {
            output.append(content);
            return;
        }

        if (!state.actionPrefixEmitted && containsNonWhitespace(content)) {
            output.append(ACTION_PREFIX);
            state.actionPrefixEmitted = true;
        }
        output.append(content);
    }

    private String openVisibleBlock(ParseState state) {
        if (state.visibleBlockOpen) {
            return "";
        }
        state.visibleBlockOpen = true;
        return OPEN_TAG;
    }

    private String closeVisibleBlock(ParseState state) {
        if (!state.visibleBlockOpen) {
            return "";
        }
        state.visibleBlockOpen = false;
        state.insideModelThink = false;
        state.actionPrefixEmitted = false;
        return CLOSE_TAG;
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
    private String stateKey(String taskId) {
        return safe(taskId) + KEY_SEPARATOR;
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

        private boolean insideModelThink;

        private boolean visibleBlockOpen;

        private boolean actionPrefixEmitted;

        private String pendingTagPrefix = "";

    }
}
