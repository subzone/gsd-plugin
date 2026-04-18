package io.jenkins.plugins.gsd.steps;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import java.io.Serializable;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Pipeline step mirroring the <a href="https://subzone.github.io/ad-ai-pr-reviewer/">AI PR Reviewer</a>
 * Azure DevOps task: review/comment on pull requests using AI (GitHub + Anthropic in this release).
 */
public class AiPrReviewerStep extends Step implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String action;
    private final String provider;
    private final String credentialsId;
    private final String repository;
    private final String prNumber;
    private final String aiCredentialsId;

    private String aiModel;
    private Integer maxDiffLines;
    private String commentBody;
    private String aiBaseUrl;
    private String aiProvider;
    private String githubApiUrl;
    private boolean enableAiReview = true;

    @DataBoundConstructor
    public AiPrReviewerStep(
            String action,
            String provider,
            String credentialsId,
            String repository,
            String prNumber,
            String aiCredentialsId) {
        this.action = Objects.requireNonNullElse(action, "reviewPR").trim();
        this.provider = Objects.requireNonNullElse(provider, "github").trim();
        this.credentialsId = Objects.requireNonNull(credentialsId, "credentialsId").trim();
        this.repository = Objects.requireNonNull(repository, "repository").trim();
        this.prNumber = Objects.requireNonNull(prNumber, "prNumber").trim();
        this.aiCredentialsId = Objects.requireNonNull(aiCredentialsId, "aiCredentialsId").trim();
    }

    public String getAction() {
        return action;
    }

    public String getProvider() {
        return provider;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getRepository() {
        return repository;
    }

    public String getPrNumber() {
        return prNumber;
    }

    public String getAiCredentialsId() {
        return aiCredentialsId;
    }

    public String getAiModel() {
        return aiModel;
    }

    @DataBoundSetter
    public void setAiModel(String aiModel) {
        this.aiModel = aiModel == null ? null : aiModel.trim();
    }

    public Integer getMaxDiffLines() {
        return maxDiffLines;
    }

    @DataBoundSetter
    public void setMaxDiffLines(Integer maxDiffLines) {
        this.maxDiffLines = maxDiffLines;
    }

    public String getCommentBody() {
        return commentBody;
    }

    @DataBoundSetter
    public void setCommentBody(String commentBody) {
        this.commentBody = commentBody;
    }

    public String getAiBaseUrl() {
        return aiBaseUrl;
    }

    @DataBoundSetter
    public void setAiBaseUrl(String aiBaseUrl) {
        this.aiBaseUrl = aiBaseUrl == null ? null : aiBaseUrl.trim();
    }

    public String getAiProvider() {
        return aiProvider;
    }

    @DataBoundSetter
    public void setAiProvider(String aiProvider) {
        this.aiProvider = aiProvider == null ? null : aiProvider.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public String getGithubApiUrl() {
        return githubApiUrl;
    }

    @DataBoundSetter
    public void setGithubApiUrl(String githubApiUrl) {
        this.githubApiUrl = githubApiUrl == null ? null : githubApiUrl.trim();
    }

    public boolean isEnableAiReview() {
        return enableAiReview;
    }

    @DataBoundSetter
    public void setEnableAiReview(boolean enableAiReview) {
        this.enableAiReview = enableAiReview;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new AiPrReviewerStepExecution(this, context);
    }

    @Extension
    @Symbol("aiPrReviewer")
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        @NonNull
        public String getDisplayName() {
            return "AI pull request reviewer (GSD)";
        }

        @Override
        @NonNull
        public String getFunctionName() {
            return "aiPrReviewer";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Set.of(hudson.model.Run.class, hudson.model.TaskListener.class);
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String value) {
            return fillStringCredentials(item, value);
        }

        @SuppressWarnings("unused")
        public ListBoxModel doFillAiCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String value) {
            return fillStringCredentials(item, value);
        }

        private static ListBoxModel fillStringCredentials(Item item, String value) {
            StandardListBoxModel m = new StandardListBoxModel();
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
                m.includeEmptyValue();
                m.includeMatchingAs(
                        ACL.SYSTEM,
                        Jenkins.get(),
                        StringCredentials.class,
                        Collections.emptyList(),
                        CredentialsMatchers.instanceOf(StringCredentials.class));
            } else {
                m.includeEmptyValue();
                m.includeMatchingAs(
                        ACL.SYSTEM,
                        item,
                        StringCredentials.class,
                        Collections.emptyList(),
                        CredentialsMatchers.instanceOf(StringCredentials.class));
            }
            m.includeCurrentValue(value);
            return m;
        }
    }
}
