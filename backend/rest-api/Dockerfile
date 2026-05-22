# syntax=docker/dockerfile:1
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/
COPY rest-api/pom.xml rest-api/
# spark-jobs is listed in the parent's <modules> so its pom.xml must exist
# for Maven to parse the reactor, even though we don't build it here.
COPY spark-jobs/pom.xml spark-jobs/
RUN mvn dependency:go-offline -B -pl rest-api --am
COPY common/src common/src
COPY rest-api/src rest-api/src
RUN mvn package -DskipTests -B -pl rest-api --am

FROM docker.io/library/eclipse-temurin:21-jre-alpine
# OCI source label so GHCR auto-links this package to the source repo.
# Without it, GITHUB_TOKEN from a release-triggered run (the actor is
# ferrflow[bot], not a repo admin) gets "denied: permission_denied:
# write_package" because the org-level package isn't linked to the
# Actions workflow that's pushing.
LABEL org.opencontainers.image.source=https://github.com/Epitech-Bryan/homepedia
LABEL org.opencontainers.image.description="HomePedia REST API — Spring Boot DVF/DPE/INSEE aggregator"
LABEL org.opencontainers.image.licenses=MIT
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/rest-api/target/*.jar app.jar
USER app
EXPOSE 8080
# JVM tuning (issue #4): G1GC with a 100 ms pause target tightens p99
# request latency — the default ParallelGC throws 300+ ms pauses under
# the heatpoints workload and they showed up in the Grafana p95 panel.
# Initial/MaxRAMPercentage scales the heap with the K8s pod memory limit
# so we don't have to re-flag per environment. HeapDumpOnOOM dumps to
# /tmp (tmpfs in the pod) for post-mortem after a kubelet restart.
# JAVA_TOOL_OPTIONS so anything inherited from the parent image stays
# intact.
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:InitialRAMPercentage=50 -XX:MaxRAMPercentage=75 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof"
CMD ["java", "-jar", "app.jar"]
