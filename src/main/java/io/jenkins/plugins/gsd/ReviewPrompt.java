package io.jenkins.plugins.gsd;

import java.util.Locale;

/** Builds the user prompt for AI review (aligned with AI PR Reviewer behaviour). */
public final class ReviewPrompt {

    private ReviewPrompt() {}

    /**
     * Builds the review prompt including PR title and description when available.
     *
     * @param repository  {@code owner/repo}
     * @param prNumber    pull request number as a string
     * @param prTitle     PR title (may be null or blank)
     * @param prBody      PR description (may be null or blank)
     * @param diffText    unified diff text
     */
    public static String build(
            String repository,
            String prNumber,
            String prTitle,
            String prBody,
            String diffText) {
        String safeRepo = repository == null ? "" : repository;
        String safePr = prNumber == null ? "" : prNumber;
        String safeDiff = diffText == null ? "" : diffText;

        StringBuilder sb = new StringBuilder();
        sb.append("You are an expert code reviewer. Analyse the pull request diff below.\n\n");
        sb.append("Repository: ").append(safeRepo).append('\n');
        sb.append("Pull request: #").append(safePr).append('\n');

        if (prTitle != null && !prTitle.isBlank()) {
            sb.append("Title: ").append(prTitle.strip()).append('\n');
        }
        if (prBody != null && !prBody.isBlank()) {
            sb.append("\nDescription:\n").append(prBody.strip()).append('\n');
        }

        sb.append(
                """

                Review the changes for bugs, security issues, performance problems, and style.
                Be specific: cite file names and line numbers where relevant.

                Produce your reply with a machine-readable header block at the VERY START, using exactly these lines:
                VERDICT: lgtm|needs-work|critical
                SUMMARY: <single line summary — max 120 characters>
                ISSUES: <non-negative integer count of distinct findings>

                Then a blank line, then a detailed markdown review for the pull request author.

                Use VERDICT values as follows:
                  lgtm     — no blocking issues found
                  needs-work — minor issues or suggestions that should be addressed
                  critical  — blocking issues such as security vulnerabilities, data loss risks, or broken logic

                Pull request diff:
                ```
                """);
        sb.append(safeDiff);
        sb.append("\n```\n");
        return sb.toString();
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
