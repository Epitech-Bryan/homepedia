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
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/rest-api/target/*.jar app.jar
USER app
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
