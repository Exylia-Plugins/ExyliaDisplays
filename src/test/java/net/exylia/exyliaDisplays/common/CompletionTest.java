package net.exylia.exyliaDisplays.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Reading the typed words and filtering what is offered by them. */
class CompletionTest {

    @Test
    @DisplayName("the word being typed is last, empty after a space")
    void words() {
        assertEquals(List.of("holo", "arena", "setline", ""), Completion.words("/holo arena setline "));
        assertEquals(List.of("holo", "arena", "setline", "3"), Completion.words("holo arena setline 3"));
    }

    @Test
    @DisplayName("suggestions continue what is typed, a multi-word one across its words")
    void matching() {
        List<String> words = Completion.words("holo arena setline 1 Hello wo");
        assertEquals(List.of("Hello world"), Completion.matching(words, List.of("Hello world", "Goodbye", "", "Hello world")));
        assertEquals(List.of("10", "12"), Completion.matching(Completion.words("holo a setline 1"), List.of("10", "2", "12")));
    }
}
