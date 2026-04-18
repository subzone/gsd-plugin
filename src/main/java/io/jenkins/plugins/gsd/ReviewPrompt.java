package io.jenkins.plugins.gsd;

import java.util.Locale;

/** Builds the user prompt for AI review (aligned with AI PR Reviewer behaviour). */
public final class ReviewPrompt {

    private ReviewPrompt() {}

    public static String build(String repository, String prNumber, String diffText) {
        String safeRepo = repository == null ? "" : repository;
        String safePr = prNumber == null ? "" : prNumber;
        String safeDiff = diffText == null ? "" : diffText;
        return """
                You are an expert code reviewer. Analyse the pull request diff below.

                Repository: __REPO__
                Pull request: #__PR__

                Produce:
                1) A concise markdown review (bugs, security, performance, style).
                2) A machine-readable header block at the VERY START of your reply, using exactly these lines:
                VERDICT: lgtm|needs-work|critical
                SUMMARY: <single line summary>
                ISSUES: <non-negative integer count of distinct findings>

                Then a blank line, then the markdown review for humans.

                Pull request diff:
                ```
                __DIFF__
                ```
                """
                .stripIndent()
                .replace("__REPO__", safeRepo)
                .replace("__PR__", safePr)
                .replace("__DIFF__", safeDiff);
    }

    public static ParsedReview parseModelOutput(String raw) {
        String verdict = "needs-work";
        String summary = "";
        int issues = 0;
        String body = raw == null ? "" : raw;
        String[] lines = body.split("\r?\n", -1);
        int i = 0;
        for (; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                i++;
                break;
            }
            if (line.regionMatches(true, 0, "VERDICT:", 0, 8)) {
                verdict = line.substring(8).trim().toLowerCase(Locale.ROOT);
            } else if (line.regionMatches(true, 0, "SUMMARY:", 0, 8)) {
                summary = line.substring(8).trim();
            } else if (line.regionMatches(true, 0, "ISSUES:", 0, 7)) {
                try {
                    issues = Integer.parseInt(line.substring(7).trim());
                } catch (NumberFormatException ignored) {
                    issues = 0;
                }
            }
        }
        StringBuilder markdown = new StringBuilder();
        for (; i < lines.length; i++) {
            markdown.append(lines[i]).append('\n');
        }
        if (!verdict.equals("lgtm") && !verdict.equals("needs-work") && !verdict.equals("critical")) {
            verdict = "needs-work";
        }
        return new ParsedReview(verdict, summary, issues, markdown.toString().trim());
    }

    public record ParsedReview(String verdict, String summary, int issues, String markdownBody) {}
}
