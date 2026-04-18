package io.jenkins.plugins.gsd;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.Run;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Exposes review outputs on the {@link Run} for tooling that honours
 * {@link EnvironmentContributingAction} (and for the build environment where supported).
 */
@Restricted(NoExternalUse.class)
public final class GsdReviewEnvAction implements EnvironmentContributingAction {

    private static final long serialVersionUID = 1L;

    private final String verdict;
    private final String summary;
    private final int issues;
    private final String prUrl;

    public GsdReviewEnvAction(String verdict, String summary, int issues, String prUrl) {
        this.verdict = verdict == null ? "" : verdict;
        this.summary = summary == null ? "" : summary;
        this.issues = issues;
        this.prUrl = prUrl == null ? "" : prUrl;
    }

    @Override
    public void buildEnvironment(Run<?, ?> run, EnvVars envs) {
        if (run == null || envs == null) {
            return;
        }
        envs.put("GSD_REVIEW_VERDICT", verdict);
        envs.put("GSD_REVIEW_SUMMARY", summary);
        envs.put("GSD_REVIEW_TOTAL_ISSUES", Integer.toString(issues));
        envs.put("GSD_PR_URL", prUrl);
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
