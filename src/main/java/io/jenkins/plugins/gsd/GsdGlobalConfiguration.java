package io.jenkins.plugins.gsd;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Defaults for {@link io.jenkins.plugins.gsd.steps.AiPrReviewerStep} (Anthropic model, diff size, API base URL).
 */
@Extension
public final class GsdGlobalConfiguration extends GlobalConfiguration {

    private String defaultAnthropicModel = "claude-sonnet-4-20250514";
    private int defaultMaxDiffLines = 500;
    private String anthropicBaseUrl = "https://api.anthropic.com";
    private String defaultGithubApiUrl = "https://api.github.com";

    public GsdGlobalConfiguration() {
        load();
    }

    public static GsdGlobalConfiguration get() {
        GsdGlobalConfiguration c = GlobalConfiguration.all().get(GsdGlobalConfiguration.class);
        return c != null ? c : new GsdGlobalConfiguration();
    }

    public String getDefaultAnthropicModel() {
        return defaultAnthropicModel;
    }

    @DataBoundSetter
    public void setDefaultAnthropicModel(String defaultAnthropicModel) {
        String v = Util.fixEmptyAndTrim(defaultAnthropicModel);
        this.defaultAnthropicModel = v == null ? "claude-sonnet-4-20250514" : v;
    }

    public int getDefaultMaxDiffLines() {
        return defaultMaxDiffLines;
    }

    @DataBoundSetter
    public void setDefaultMaxDiffLines(int defaultMaxDiffLines) {
        this.defaultMaxDiffLines = defaultMaxDiffLines > 0 ? defaultMaxDiffLines : 500;
    }

    public String getAnthropicBaseUrl() {
        return anthropicBaseUrl;
    }

    @DataBoundSetter
    public void setAnthropicBaseUrl(String anthropicBaseUrl) {
        String v = Util.fixEmptyAndTrim(anthropicBaseUrl);
        this.anthropicBaseUrl = v == null ? "https://api.anthropic.com" : v.replaceAll("/+$", "");
    }

    public String getDefaultGithubApiUrl() {
        return defaultGithubApiUrl;
    }

    @DataBoundSetter
    public void setDefaultGithubApiUrl(String defaultGithubApiUrl) {
        String v = Util.fixEmptyAndTrim(defaultGithubApiUrl);
        this.defaultGithubApiUrl = v == null ? "https://api.github.com" : v.replaceAll("/+$", "");
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) {
        req.bindJSON(this, json);
        save();
        return true;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "GSD / AI pull request reviewer";
    }
}
