package com.example.dataprocess.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThinkVisibleTextAdapterTest {

    private final ThinkVisibleTextAdapter adapter = new ThinkVisibleTextAdapter();

    @Test
    void wrapsActionTextAfterThinkBlock() {
        String adapted = adapter.adapt("task-1", "agent", "<think>analyze</think>call tool");

        assertThat(adapted).isEqualTo("<think>analyze</think><think>[\u884c\u52a8]\ncall tool</think>");
    }

    @Test
    void wrapsPlainTextAsActionWhenNoThinkTagExists() {
        String adapted = adapter.adapt("task-1", "agent", "call tool");

        assertThat(adapted).isEqualTo("<think>[\u884c\u52a8]\ncall tool</think>");
    }

    @Test
    void handlesSplitOpenAndCloseTagsAcrossChunks() {
        assertThat(adapter.adapt("task-1", "agent", "<thi")).isEmpty();
        assertThat(adapter.adapt("task-1", "agent", "nk>analysis")).isEqualTo("<think>analysis</think>");
        assertThat(adapter.adapt("task-1", "agent", "</thi")).isEmpty();
        assertThat(adapter.adapt("task-1", "agent", "nk>action")).isEqualTo("<think>[\u884c\u52a8]\naction</think>");
    }

    @Test
    void emitsActionPrefixOnlyOnceForContiguousOutsideThinkText() {
        assertThat(adapter.adapt("task-1", "agent", "</think>first")).isEqualTo("<think>[\u884c\u52a8]\nfirst</think>");
        assertThat(adapter.adapt("task-1", "agent", " second")).isEqualTo("<think> second</think>");
    }

    @Test
    void keepsStateSeparateByTaskAndNode() {
        assertThat(adapter.adapt("task-1", "agent", "<think>one")).isEqualTo("<think>one</think>");
        assertThat(adapter.adapt("task-2", "agent", "two")).isEqualTo("<think>[\u884c\u52a8]\ntwo</think>");
        assertThat(adapter.adapt("task-1", "agent", "</think>three")).isEqualTo("<think>[\u884c\u52a8]\nthree</think>");
    }

    @Test
    void clearTaskDropsParserState() {
        assertThat(adapter.adapt("task-1", "agent", "<think>analysis")).isEqualTo("<think>analysis</think>");

        adapter.clearTask("task-1");

        assertThat(adapter.adapt("task-1", "agent", "action")).isEqualTo("<think>[\u884c\u52a8]\naction</think>");
    }
}
