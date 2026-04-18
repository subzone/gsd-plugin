// ci.jenkins.io only: requires https://github.com/jenkins-infra/pipeline-library
// (not available on a stock Jenkins — use `mvn verify` / Docker locally; see README.)
buildPlugin(useContainerAgent: true, configurations: [
  [platform: 'linux', jdk: 21],
  [platform: 'windows', jdk: 21],
])
