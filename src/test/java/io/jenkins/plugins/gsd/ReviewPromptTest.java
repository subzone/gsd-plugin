package io.jenkins.plugins.gsd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void parseModelOutput_defaultsToNeedsWork_onUnknownVerdict() {
        String raw = "VERDICT: unknown-value\nSUMMARY: test\nISSUES: 1\n\nsome body";
        ReviewPrompt.ParsedReview p = ReviewPrompt.parseModelOutput(raw);
        assertEquals("needs-work", p.verdict());
    }

    @Test
    public void parseModelOutput_handlesNullInput() {
        ReviewPrompt.ParsedReview p = ReviewPrompt.parseModelOutput(null);
        assertEquals("needs-work", p.verdict());
        assertEquals("", p.summary());
        assertEquals(0, p.issues());
    }

    @Test
    public void build_includesTitleAndBody_whenProvided() {
        String prompt = ReviewPrompt.build("owner/repo", "42", "Fix auth bug", "Detailed description here", "--- a/foo.java\n+++ b/foo.java");
        assertTrue(prompt.contains("Title: Fix auth bug"));
        assertTrue(prompt.contains("Detailed description here"));
        assertTrue(prompt.contains("Repository: owner/repo"));
        assertTrue(prompt.contains("Pull request: #42"));
    }

    @Test
    public void build_omitsTitleSection_whenTitleIsNull() {
        String prompt = ReviewPrompt.build("owner/repo", "1", null, null, "diff text");
        assertFalse(prompt.contains("Title:"));
        assertFalse(prompt.contains("Description:"));
        assertTrue(prompt.contains("diff text"));
    }

    @Test
    public void build_omitsTitleSection_whenTitleIsBlank() {
        String prompt = ReviewPrompt.build("owner/repo", "1", "  ", "", "diff text");
        assertFalse(prompt.contains("Title:"));
    }

    @Test
    public void extractFirstTextBlock_readsQuotedValue() throws IOException {
        String json = "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}";
        assertEquals("hello", AnthropicMessages.extractFirstTextBlock(json));
    }

    @Test
    public void extractFirstTextBlock_handlesEscapes() throws IOException {
        String json = "{\"text\":\"line1\\nline2\"}";
        assertEquals("line1\nline2", AnthropicMessages.extractFirstTextBlock(json));
    }

    @Test
    public void extractJsonStringField_readsField() {
        String json = "{\"number\":42,\"title\":\"Fix bug\",\"body\":\"some description\"}";
        assertEquals("Fix bug", GithubRest.extractJsonStringField(json, "title"));
        assertEquals("some description", GithubRest.extractJsonStringField(json, "body"));
    }

    @Test
    public void extractJsonStringField_returnsEmpty_onNullField() {
        String json = "{\"title\":\"t\",\"body\":null}";
        assertEquals("t", GithubRest.extractJsonStringField(json, "title"));
        assertEquals("", GithubRest.extractJsonStringField(json, "body"));
    }

    @Test
    public void extractJsonStringField_returnsEmpty_onMissingField() {
        String json = "{\"title\":\"t\"}";
        assertEquals("", GithubRest.extractJsonStringField(json, "missing"));
    }

    // ── OpenAiMessages ───────────────────────────────────────────────────────

    @Test
    public void extractContent_readsAssistantMessage() throws IOException {
        String response = "{\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"Great PR!\"},"
                + "\"finish_reason\":\"stop\"}]}";
        assertEquals("Great PR!", OpenAiMessages.extractContent(response));
    }

    @Test
    public void extractContent_handlesEscapes() throws IOException {
        String response = "{\"choices\":[{\"message\":{\"content\":\"line1\\nline2\"}}]}";
        assertEquals("line1\nline2", OpenAiMessages.extractContent(response));
    }

    @Test(expected = IOException.class)
    public void extractContent_throwsOnMissingContent() throws IOException {
        OpenAiMessages.extractContent("{\"choices\":[{\"message\":{\"role\":\"assistant\"}}]}");
    }
}
