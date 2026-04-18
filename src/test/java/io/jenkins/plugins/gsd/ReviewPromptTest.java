package io.jenkins.plugins.gsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.junit.Test;

public class ReviewPromptTest {

    @Test
    public void parseModelOutput_readsHeaders() {
        String raw =
                """
                VERDICT: lgtm
                SUMMARY: Looks good
                ISSUES: 0

                ## Notes
                Ship it.
                """;
        ReviewPrompt.ParsedReview p = ReviewPrompt.parseModelOutput(raw);
        assertEquals("lgtm", p.verdict());
        assertEquals("Looks good", p.summary());
        assertEquals(0, p.issues());
        assertTrue(p.markdownBody().contains("Ship it."));
    }

    @Test
    public void extractFirstTextBlock_readsQuotedValue() throws IOException {
        String json = "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}";
        assertEquals("hello", AnthropicMessages.extractFirstTextBlock(json));
    }
}
