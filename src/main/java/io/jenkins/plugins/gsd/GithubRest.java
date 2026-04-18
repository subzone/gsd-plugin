package io.jenkins.plugins.gsd;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** Minimal GitHub REST client (pull diff, PR metadata, issue comments, PR reviews). */
public final class GithubRest {

    private static final String DEFAULT_API_URL = "https://api.github.com";

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    private GithubRest() {}

    /** Metadata returned by {@link #fetchPullRequestMeta}. */
    public record PullRequestMeta(String title, String body) {}

    /** Fetches the unified diff for a pull request. */
    public static String fetchPullRequestDiff(String token, String owner, String repo, String prNumber, String apiUrl)
            throws IOException, InterruptedException {
        String root = normalizeApiUrl(apiUrl);
        String path = String.format("/repos/%s/%s/pulls/%s", owner, repo, prNumber);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(root + path))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github.diff")
                .header("User-Agent", "Jenkins-GSD-Plugin")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
            return Objects.requireNonNullElse(res.body(), "");
        }
        throw new IOException("GitHub API error " + res.statusCode() + ": " + abbreviate(res.body()));
    }

    /** Fetches the title and body of a pull request. Returns empty strings on missing fields. */
    public static PullRequestMeta fetchPullRequestMeta(
            String token, String owner, String repo, String prNumber, String apiUrl)
            throws IOException, InterruptedException {
        String root = normalizeApiUrl(apiUrl);
        String path = String.format("/repos/%s/%s/pulls/%s", owner, repo, prNumber);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(root + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Jenkins-GSD-Plugin")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IOException("GitHub API error " + res.statusCode() + ": " + abbreviate(res.body()));
        }
        String json = Objects.requireNonNullElse(res.body(), "");
        return new PullRequestMeta(
                extractJsonStringField(json, "title"), extractJsonStringField(json, "body"));
    }

    /**
     * Posts a formal pull request review (APPROVE / REQUEST_CHANGES / COMMENT).
     *
     * @param event one of {@code APPROVE}, {@code REQUEST_CHANGES}, {@code COMMENT}
     */
    public static void postPullRequestReview(
            String token,
            String owner,
            String repo,
            String prNumber,
            String body,
            String event,
            String apiUrl)
            throws IOException, InterruptedException {
        String root = normalizeApiUrl(apiUrl);
        String path = String.format("/repos/%s/%s/pulls/%s/reviews", owner, repo, prNumber);
        String json = "{\"body\":" + escapeJson(body) + ",\"event\":\"" + event + "\"}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(root + path))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Jenkins-GSD-Plugin")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
            return;
        }
        throw new IOException("GitHub API error " + res.statusCode() + ": " + abbreviate(res.body()));
    }

    /** Posts a plain issue / PR comment (fallback or used by commentPR action). */
    public static void postIssueComment(
            String token, String owner, String repo, String issueNumber, String body, String apiUrl)
            throws IOException, InterruptedException {
        String root = normalizeApiUrl(apiUrl);
        String path = String.format("/repos/%s/%s/issues/%s/comments", owner, repo, issueNumber);
        String json = "{\"body\":" + escapeJson(body) + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(root + path))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Jenkins-GSD-Plugin")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() >= 200 && res.statusCode() < 300) {
            return;
        }
        throw new IOException("GitHub API error " + res.statusCode() + ": " + abbreviate(res.body()));
    }

    private static String normalizeApiUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return DEFAULT_API_URL;
        }
        return apiUrl.replaceAll("/+$", "");
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 512 ? s.substring(0, 512) + "…" : s;
    }

    /** Minimal JSON string escape for comment bodies. */
    public static String escapeJson(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Extracts a JSON string field value by name without a JSON library.
     * Returns empty string if the field is absent or {@code null}.
     */
    static String extractJsonStringField(String json, String fieldName) {
        String key = "\"" + fieldName + "\":";
        int at = json.indexOf(key);
        if (at < 0) {
            return "";
        }
        int i = at + key.length();
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return "";
        }
        if (json.charAt(i) == 'n') {
            return ""; // null literal
        }
        if (json.charAt(i) != '"') {
            return "";
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
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
                        if (i + 5 < json.length()) {
                            sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
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
        return sb.toString();
    }
}
