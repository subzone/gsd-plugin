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

The root `Jenkinsfile` uses `buildPlugin` and only works on [ci.jenkins.io](https://ci.jenkins.io) with the infra pipeline library. For your controller, create a **Pipeline** job from this repo and set **Script Path** to **`Jenkinsfile.local`**.

By default that pipeline runs **`docker compose run --rm ci`**, so the agent needs **Docker** (Compose v2: `docker compose`) and permission to talk to the daemon. The script prepends typical **macOS** locations (`Docker.app` CLI, Homebrew) because Jenkins often runs with a minimal `PATH` and cannot find `docker` otherwise. If it still fails, set a global or node environment variable **`GSD_EXTRA_PATH`** to the directory that contains the `docker` binary, or add that directory under **Manage Jenkins → System** (or node properties) to **PATH**. If the agent already has **JDK 17+** and **Maven** on `PATH`, disable the **USE_DOCKER** build parameter instead.

### Install and test on your controller

1. Build **`target/gsd.hpi`** (`mvn package` or your `Jenkinsfile.local` job).
2. **Manage Jenkins → Plugins → Advanced** (or **Available plugins** upload): install the `.hpi`, then **restart** when Jenkins asks. Accept any offered **dependency** installs (Workflow API, Structs, Credentials, Plain Credentials, and Pipeline-related plugins if you use Pipelines).
3. **Manage Jenkins → Credentials**: add **Secret text** credentials for your GitHub PAT and Anthropic API key (see below for scopes).
4. **New Item → Pipeline** (agent needs outbound HTTPS to `api.github.com` and `api.anthropic.com`). Start with **`commentPR`** on a throwaway PR so you verify GitHub access without spending tokens on AI:

```groovy
node {
  aiPrReviewer(
    action: 'commentPR',
    provider: 'github',
    credentialsId: 'github-pat',
    repository: 'owner/repo',
    prNumber: '123',
    commentBody: 'GSD smoke test from Jenkins',
    enableAiReview: false,
    aiCredentialsId: 'unused' // not read for commentPR; any non-empty id satisfies the step signature
  )
}
```

Check the PR on GitHub for the comment, then try **`reviewPR`** with `enableAiReview: true` and a real `aiCredentialsId` as in the next section.

#### Plugin page “health” or odd icons

- **Manage Jenkins → Plugins** may warn for plugins **not** installed from the public Update Center (e.g. a manually uploaded `.hpi` or a `-SNAPSHOT` build). That is normal and does not mean the plugin is broken.
- The **[Plugin Health Score](https://www.jenkins.io/blog/2023/10/25/what-is-the-plugin-health-score/)** on [plugins.jenkins.io](https://plugins.jenkins.io/) is computed only for plugins **listed in the official update center**. Until GSD is published there, there is no score (or the UI can look incomplete). After [hosting under `jenkinsci`](https://www.jenkins.io/doc/developer/publishing/requesting-hosting/), that data can populate over time.

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
