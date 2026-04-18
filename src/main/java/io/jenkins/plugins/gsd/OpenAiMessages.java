package io.jenkins.plugins.gsd;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * OpenAI-compatible chat completions client.
 * Works with OpenAI, LiteLLM, Ollama, vLLM, and any other provider
 * that implements {@code POST /v1/chat/completions}.
 */
public final class OpenAiMessages {

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    private OpenAiMessages() {}

    public static String complete(String apiKey, String baseUrl, String model, String userPrompt)
            throws IOException, InterruptedException {
        String root = Objects.requireNonNullElse(baseUrl, "https://api.openai.com").replaceAll("/+$", "");
        String json = "{\"model\":"
                + GithubRest.escapeJson(model)
                + ",\"max_tokens\":8192"
                + ",\"messages\":[{\"role\":\"user\",\"content\":"
                + GithubRest.escapeJson(userPrompt)
                + "}]}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(root + "/v1/chat/completions"))
                .timeout(Duration.ofMinutes(5))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("OpenAI-compatible API error " + res.statusCode() + ": " + abbreviate(res.body()));
        }
        return extractContent(Objects.requireNonNullElse(res.body(), ""));
    }

    /**
     * Extracts {@code choices[0].message.content} from a chat-completions JSON response
     * without a JSON library.
     * The first {@code "content":} key in the response is always the assistant message content.
     */
    public static String extractContent(String body) throws IOException {
        String content = GithubRest.extractJsonStringField(body, "content");
        if (content.isEmpty()) {
            throw new IOException(
                    "Unexpected OpenAI-compatible response (no content field): " + abbreviate(body));
        }
        return content;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 512 ? s.substring(0, 512) + "…" : s;
    }
}
