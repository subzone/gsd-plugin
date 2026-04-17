# Local CI image: JDK 21 + Maven 3.9 (matches Jenkinsfile JDK 21; pom uses standard <scm>).
# Source is bind-mounted by docker-compose; this layer only pins the toolchain.
FROM maven:3.9.9-eclipse-temurin-21

WORKDIR /workspace

# Full plugin check: tests, SpotBugs, etc. (same as Jenkins buildPlugin verify path)
CMD ["mvn", "-B", "--no-transfer-progress", "verify"]
