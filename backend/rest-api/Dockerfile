FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/
COPY rest-api/pom.xml rest-api/
COPY data-pipeline/pom.xml data-pipeline/
RUN mvn dependency:go-offline -B -pl rest-api --am
COPY rest-api/src rest-api/src
RUN mvn package -DskipTests -B -pl rest-api --am

FROM docker.io/library/eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/rest-api/target/*.jar app.jar
USER app
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
