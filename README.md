# GSD (`gsd`)

Jenkins plugin (`.hpi`) for Pipeline-oriented pull request automation and AI-assisted review. This repository is a **hosting-oriented skeleton**: metadata, CI, and a minimal extension point are in place so you can iterate toward a full implementation and then request inclusion on the **public Update Center**.

## Metadata you must personalize before hosting

| Location | What to change |
|----------|------------------|
| `pom.xml` → `<developers>` | Replace `YOUR_GITHUB_ID` / `Your Name` with your Jenkins community account id and display name. |
| `pom.xml` → `<gitHubRepo>` | Before the `jenkinsci` fork exists, point at your public GitHub repo (for example `yourname/gsd-plugin`). After hosting, it should be `jenkinsci/gsd-plugin`. |
| `LICENSE` | Adjust the copyright line if you are not the sole rights holder. |

Permanent choices (do not rename after the first release to Maven):

- **Group ID:** `io.jenkins.plugins`
- **Artifact ID / plugin id:** `gsd`

## Build

```bash
mvn verify
```

## Publish to the Update Center

Follow the official flow: [Guide to Plugin Hosting](https://www.jenkins.io/doc/developer/publishing/requesting-hosting/) (preparation, hosting issue in `repository-permissions-updater`, CI on `jenkinsci`, release permissions, then [releasing](https://www.jenkins.io/doc/developer/publishing/releasing/)).

For plugin-site labels, use [GitHub repository topics](https://www.jenkins.io/doc/developer/publishing/documentation/#using-github-topics) that appear on the [allowlist](https://github.com/jenkins-infra/update-center2/blob/master/resources/allowed-github-topics.properties).
# gsd-plugin
