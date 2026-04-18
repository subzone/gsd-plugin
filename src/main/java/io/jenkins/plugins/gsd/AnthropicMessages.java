package io.jenkins.plugins.gsd;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** Anthropic Messages API (https://docs.anthropic.com/claude/reference/messages_post). */
public final class AnthropicMessages {

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    private AnthropicMessages() {}

    public static String complete(String apiKey, String baseUrl, String model, String userPrompt)
            throws IOException, InterruptedException {
        String root = Objects.requireNonNullElse(baseUrl, "https://api.anthropic.com").replaceAll("/+$", "");
        String json =
                """
                        {"model":__MODEL__,"max_tokens":8192,"messages":[{"role":"user","content":[{"type":"text","text":__TEXT__}]}]}
                        """
                        .stripIndent()
                        .replace("__MODEL__", GithubRest.escapeJson(model))
                        .replace("__TEXT__", GithubRest.escapeJson(userPrompt));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(root + "/v1/messages"))
                .timeout(Duration.ofMinutes(5))
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("Anthropic API error " + res.statusCode() + ": " + abbreviate(res.body()));
        }
        return extractFirstTextBlock(Objects.requireNonNullElse(res.body(), ""));
    }

    /**
     * Extracts the first {@code "text":"..."} value from a Messages API JSON body without a JSON library.
     */
    public static String extractFirstTextBlock(String body) throws IOException {
        String key = "\"text\":";
        int at = body.indexOf(key);
        if (at < 0) {
            throw new IOException("Unexpected Anthropic response shape (no text field)");
        }
        int i = at + key.length();
        while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
            i++;
        }
        if (i >= body.length() || body.charAt(i) != '"') {
            throw new IOException("Unexpected Anthropic response shape");
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\\') {
                if (i + 1 >= body.length()) {
                    break;
                }
                char n = body.charAt(i + 1);
                switch (n) {
                    case '"':
                        sb.append('"');
                        i += 2;
                        continue;
                    case '\\':
                        sb.append('\\');
                        i += 2;
                        continue;
                    case 'n':
                        sb.append('\n');
                        i += 2;
                        continue;
                    case 'r':
                        sb.append('\r');
                        i += 2;
                        continue;
                    case 't':
                        sb.append('\t');
                        i += 2;
                        continue;
                    case 'u':
                        if (i + 5 < body.length()) {
                            String hex = body.substring(i + 2, i + 6);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 6;
                            continue;
                        }
                        break;
                    default:
                        sb.append(n);
                        i += 2;
                        continue;
                }
            }
            if (c == '"') {
                return sb.toString();
            }
            sb.append(c);
            i++;
        }
        throw new IOException("Unterminated string in Anthropic response");
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 512 ? s.substring(0, 512) + "…" : s;
    }
}
