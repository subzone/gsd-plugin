package io.jenkins.plugins.gsd;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/** Minimal GitHub REST client (pull diff + issue comments). */
public final class GithubRest {

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    private GithubRest() {}

    public static String fetchPullRequestDiff(String token, String owner, String repo, String prNumber)
            throws IOException, InterruptedException {
        String path = String.format("/repos/%s/%s/pulls/%s", owner, repo, prNumber);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com" + path))
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

    public static void postIssueComment(String token, String owner, String repo, String issueNumber, String body)
            throws IOException, InterruptedException {
        String path = String.format("/repos/%s/%s/issues/%s/comments", owner, repo, issueNumber);
        String json = "{\"body\":" + escapeJson(body) + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com" + path))
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

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 512 ? s.substring(0, 512) + "…" : s;
    }

    /** Minimal JSON string escape for comment body (no control chars expected). */
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
}
