# GSD (`gsd`)

Jenkins plugin (`.hpi`) for Pipeline-oriented pull request automation and AI-assisted review, aligned with the behaviour of [AI PR Reviewer](https://subzone.github.io/ad-ai-pr-reviewer/) (Azure DevOps). The `aiPrReviewer` step supports **GitHub** plus **Anthropic** today; other git hosts and AI providers can be added incrementally. For Update Center publication, see hosting notes below.

## Metadata you must personalize before hosting

| Location | What to change |
|----------|------------------|
| `pom.xml` → `<developers>` | Ensure `<id>` matches your Jenkins community account id (often your GitHub id). |
| `pom.xml` → `<gitHubRepo>` | Point at your GitHub repo (`owner/name`). After hosting under `jenkinsci`, switch to `jenkinsci/gsd-plugin`. |
| `LICENSE` | Adjust the copyright line if you are not the sole rights holder. |

Permanent choices (do not rename after the first release to Maven):

- **Group ID:** `io.jenkins.plugins`
- **Artifact ID / plugin id:** `gsd`

## Build

```bash
mvn verify
```

Docker (Colima / Docker Desktop):

```bash
docker compose run --rm ci
```

### Build on your own Jenkins

The root `Jenkinsfile` uses `buildPlugin` and only works on [ci.jenkins.io](https://ci.jenkins.io) with the infra pipeline library. For your controller, create a **Pipeline** job from this repo and set **Script Path** to **`Jenkinsfile.local`**. The agent needs **JDK 17+** and **Maven** on `PATH`; otherwise change the Verify stage to `sh 'docker compose run --rm ci'` (Docker required on the agent).

## Pipeline (`aiPrReviewer`)

Create two **Secret text** credentials in Jenkins: a GitHub PAT (repo + PR read, issues write for comments) and an Anthropic API key.

```groovy
aiPrReviewer(
  action: 'reviewPR',
  provider: 'github',
  credentialsId: 'github-pat',
  repository: 'org/repo',
  prNumber: "${env.CHANGE_ID}", // or a literal when not multibranch
  aiCredentialsId: 'anthropic-api-key',
  // optional: aiModel, maxDiffLines, aiBaseUrl (defaults in Manage Jenkins → GSD)
)
```

`reviewPR` fetches the PR diff, calls Anthropic, and posts a single PR comment. Outputs are written to `.gsd/last-review.properties` on the workspace (when a workspace exists) and exposed on the build via `GSD_REVIEW_VERDICT`, `GSD_REVIEW_SUMMARY`, `GSD_REVIEW_TOTAL_ISSUES`, `GSD_PR_URL` where the environment contributor applies.

`commentPR` posts `commentBody` as an issue comment. `createPR` is not implemented yet.

## Publish to the Update Center

Follow the official flow: [Guide to Plugin Hosting](https://www.jenkins.io/doc/developer/publishing/requesting-hosting/) (preparation, hosting issue in `repository-permissions-updater`, CI on `jenkinsci`, release permissions, then [releasing](https://www.jenkins.io/doc/developer/publishing/releasing/)).

For plugin-site labels, use [GitHub repository topics](https://www.jenkins.io/doc/developer/publishing/documentation/#using-github-topics) that appear on the [allowlist](https://github.com/jenkins-infra/update-center2/blob/master/resources/allowed-github-topics.properties).
