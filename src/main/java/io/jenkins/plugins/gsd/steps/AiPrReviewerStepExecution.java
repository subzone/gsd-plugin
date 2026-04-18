package io.jenkins.plugins.gsd.steps;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import io.jenkins.plugins.gsd.AnthropicMessages;
import io.jenkins.plugins.gsd.GithubRest;
import io.jenkins.plugins.gsd.GsdGlobalConfiguration;
import io.jenkins.plugins.gsd.GsdReviewEnvAction;
import io.jenkins.plugins.gsd.OpenAiMessages;
import io.jenkins.plugins.gsd.ReviewPrompt;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class AiPrReviewerStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

    private static final long serialVersionUID = 1L;

    private final AiPrReviewerStep step;

    AiPrReviewerStepExecution(AiPrReviewerStep step, StepContext context) {
        super(context);
        this.step = step;
    }

    @Override
    protected Void run() throws Exception {
        Run<?, ?> run = Objects.requireNonNull(getContext().get(Run.class), "Run");
        TaskListener listener = Objects.requireNonNull(getContext().get(TaskListener.class), "TaskListener");
        FilePath ws = getContext().get(FilePath.class);

        String action = step.getAction().toLowerCase(Locale.ROOT);
        String provider = step.getProvider().toLowerCase(Locale.ROOT);

        if (!"github".equals(provider)) {
            throw new AbortException(
                    "Git provider '" + step.getProvider() + "' is not implemented yet. Use provider: 'github'.");
        }

        String[] coords = parseRepository(step.getRepository());
        String owner = coords[0];
        String repo = coords[1];

        GsdGlobalConfiguration global = GsdGlobalConfiguration.get();
        String githubApiUrl = resolveGithubApiUrl(global);
        String prUrl = normalizeGithubBaseUrl(githubApiUrl) + "/" + owner + "/" + repo + "/pull/" + step.getPrNumber();

        String gitToken = resolveSecret(step.getCredentialsId(), run);

        switch (action) {
            case "reviewpr" -> {
                if (!step.isEnableAiReview()) {
                    listener.getLogger()
                            .println("[GSD] AI review skipped because enableAiReview=false.");
                    return null;
                }
                runReview(owner, repo, gitToken, global, githubApiUrl, run, listener, ws, prUrl);
            }
            case "commentpr" -> runComment(owner, repo, gitToken, githubApiUrl, listener);
            case "createpr" -> throw new AbortException(
                    "action 'createPR' is not implemented in this GSD plugin version yet.");
            default -> throw new AbortException("Unknown action '" + step.getAction()
                    + "'. Expected reviewPR, commentPR, or createPR.");
        }
        return null;
    }

    private void runComment(
            String owner, String repo, String gitToken, String githubApiUrl, TaskListener listener)
            throws IOException, InterruptedException {
        String body = step.getCommentBody();
        if (body == null || body.isBlank()) {
            throw new AbortException("commentBody is required when action is commentPR.");
        }
        GithubRest.postIssueComment(gitToken, owner, repo, step.getPrNumber(), body, githubApiUrl);
        listener.getLogger().println("[GSD] Posted comment on pull request #" + step.getPrNumber());
    }

    private void runReview(
            String owner,
            String repo,
            String gitToken,
            GsdGlobalConfiguration global,
            String githubApiUrl,
            Run<?, ?> run,
            TaskListener listener,
            FilePath ws,
            String prUrl)
            throws Exception {
        // Fetch diff
        String diff = GithubRest.fetchPullRequestDiff(gitToken, owner, repo, step.getPrNumber(), githubApiUrl);
        int max = step.getMaxDiffLines() != null && step.getMaxDiffLines() > 0
                ? step.getMaxDiffLines()
                : global.getDefaultMaxDiffLines();
        String truncated = truncateLines(diff, max, listener);

        // Fetch PR metadata (title + description) for richer review context
        GithubRest.PullRequestMeta meta = null;
        try {
            meta = GithubRest.fetchPullRequestMeta(gitToken, owner, repo, step.getPrNumber(), githubApiUrl);
        } catch (IOException ex) {
            listener.getLogger()
                    .println("[GSD] Could not fetch PR metadata (continuing without it): " + ex.getMessage());
        }
        String prTitle = meta != null ? meta.title() : null;
        String prBody = meta != null ? meta.body() : null;

        // Resolve AI settings
        String model = step.getAiModel() != null && !step.getAiModel().isBlank()
                ? step.getAiModel()
                : global.getDefaultAnthropicModel();
        String apiKey = resolveSecret(step.getAiCredentialsId(), run);
        String baseUrl = step.getAiBaseUrl() != null && !step.getAiBaseUrl().isBlank()
                ? step.getAiBaseUrl()
                : global.getAnthropicBaseUrl();

        // Resolve AI provider (step override → global default)
        String aiProvider = step.getAiProvider() != null && !step.getAiProvider().isBlank()
                ? step.getAiProvider()
                : global.getDefaultAiProvider();

        // Build and send prompt
        String prompt = ReviewPrompt.build(step.getRepository(), step.getPrNumber(), prTitle, prBody, truncated);
        listener.getLogger().println("[GSD] Calling " + aiProvider + " model " + model + "…");
        String raw = callAiProvider(apiKey, baseUrl, model, prompt, aiProvider);
        ReviewPrompt.ParsedReview parsed = ReviewPrompt.parseModelOutput(raw);

        // Post review — use GitHub PR Review API for a proper APPROVE / REQUEST_CHANGES / COMMENT
        String markdown = buildPostedBody(parsed, raw, prTitle);
        String reviewEvent = verdictToReviewEvent(parsed.verdict());
        try {
            GithubRest.postPullRequestReview(
                    gitToken, owner, repo, step.getPrNumber(), markdown, reviewEvent, githubApiUrl);
        } catch (IOException ex) {
            // Fall back to a plain comment if the review API fails (e.g. insufficient token scope)
            listener.getLogger()
                    .println("[GSD] PR Review API failed (" + ex.getMessage() + "); falling back to issue comment.");
            GithubRest.postIssueComment(gitToken, owner, repo, step.getPrNumber(), markdown, githubApiUrl);
        }

        listener.getLogger().println("[GSD] Posted AI review on pull request #" + step.getPrNumber());
        listener.getLogger().println("[GSD] Verdict=" + parsed.verdict() + " issues=" + parsed.issues());

        run.addAction(new GsdReviewEnvAction(parsed.verdict(), parsed.summary(), parsed.issues(), prUrl));
        writeWorkspaceSummary(ws, parsed, prUrl, listener);
    }

    /**
     * Routes the completion request to the correct AI backend.
     *
     * @param provider {@code "anthropic"} (default) or {@code "openai"} for any
     *                 OpenAI-compatible endpoint (LiteLLM, vLLM, Ollama, …)
     */
    private static String callAiProvider(
            String apiKey, String baseUrl, String model, String prompt, String provider)
            throws IOException, InterruptedException {
        if ("openai".equals(provider)) {
            return OpenAiMessages.complete(apiKey, baseUrl, model, prompt);
        }
        return AnthropicMessages.complete(apiKey, baseUrl, model, prompt);
    }

    /** Maps a verdict string to a GitHub PR review event. */
    private static String verdictToReviewEvent(@NonNull String verdict) {        return switch (verdict) {
            case "lgtm" -> "APPROVE";
            case "critical" -> "REQUEST_CHANGES";
            default -> "COMMENT";
        };
    }

    private static String buildPostedBody(ReviewPrompt.ParsedReview parsed, String raw, String prTitle) {
        String body = parsed.markdownBody();
        if (body == null || body.isBlank()) {
            body = raw == null ? "_Empty review response._" : raw;
        }
        String titleLine = prTitle != null && !prTitle.isBlank()
                ? "**PR: " + prTitle + "**\n\n"
                : "";
        return "<!-- gsd-plugin ai review -->\n\n" + titleLine + body;
    }

    private static void writeWorkspaceSummary(
            FilePath ws, ReviewPrompt.ParsedReview parsed, String prUrl, TaskListener listener) {
        if (ws == null) {
            return;
        }
        try {
            FilePath dir = ws.child(".gsd");
            dir.mkdirs();
            String props = "GSD_REVIEW_VERDICT="
                    + escapeProps(parsed.verdict()) + "\nGSD_REVIEW_SUMMARY="
                    + escapeProps(parsed.summary()) + "\nGSD_REVIEW_TOTAL_ISSUES="
                    + parsed.issues() + "\nGSD_PR_URL=" + escapeProps(prUrl) + "\n";
            dir.child("last-review.properties").write(props, StandardCharsets.UTF_8.name());
            listener.getLogger().println("[GSD] Wrote " + dir.child("last-review.properties").getRemote());
        } catch (IOException | InterruptedException ex) {
            listener.getLogger().println("[GSD] Could not write .gsd/last-review.properties: " + ex.getMessage());
        }
    }

    private static String escapeProps(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\n", " ").replace("\r", "");
    }

    private static String truncateLines(String diff, int maxLines, TaskListener listener) {
        String[] lines = diff.split("\r?\n", -1);
        if (lines.length <= maxLines) {
            return diff;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]).append('\n');
        }
        sb.append("\n… [GSD] Diff truncated to ")
                .append(maxLines)
                .append(" lines (")
                .append(lines.length)
                .append(" total). …\n");
        listener.getLogger().println("[GSD] Truncated diff from " + lines.length + " lines to " + maxLines);
        return sb.toString();
    }

    private static String[] parseRepository(String repository) throws AbortException {
        String r = repository.trim();
        int slash = r.indexOf('/');
        if (slash <= 0 || slash == r.length() - 1) {
            throw new AbortException("repository must look like 'owner/name' but was: " + repository);
        }
        return new String[] {r.substring(0, slash), r.substring(slash + 1)};
    }

    /**
     * Resolves the effective GitHub API URL from step override → global default → built-in default.
     * Returns e.g. {@code https://api.github.com} or {@code https://github.example.com/api/v3}.
     */
    private String resolveGithubApiUrl(GsdGlobalConfiguration global) {
        if (step.getGithubApiUrl() != null && !step.getGithubApiUrl().isBlank()) {
            return step.getGithubApiUrl().strip().replaceAll("/+$", "");
        }
        return global.getDefaultGithubApiUrl();
    }

    /**
     * Derives the human-facing GitHub base URL from the API URL.
     * {@code https://api.github.com} → {@code https://github.com}
     * Everything else (GitHub Enterprise) is returned as-is after stripping trailing slashes.
     */
    private static String normalizeGithubBaseUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank() || "https://api.github.com".equals(apiUrl)) {
            return "https://github.com";
        }
        return apiUrl.replaceAll("/+$", "");
    }

    private static String resolveSecret(String credentialsId, Run<?, ?> run) throws AbortException {
        Job<?, ?> job = run.getParent();
        Authentication auth = ACL.SYSTEM;
        StringCredentials cred = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StringCredentials.class,
                        job,
                        auth,
                        Collections.<DomainRequirement>emptyList()),
                CredentialsMatchers.withId(credentialsId));
        if (cred == null) {
            throw new AbortException("Could not find string credentials with id '" + credentialsId + "'.");
        }
        return cred.getSecret().getPlainText();
    }
}
