# syntax=docker/dockerfile:1
FROM docker.io/library/maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY common/pom.xml common/
COPY rest-api/pom.xml rest-api/
COPY spark-jobs/pom.xml spark-jobs/
RUN mvn dependency:go-offline -B -pl spark-jobs --am
COPY spark-jobs/src spark-jobs/src
RUN mvn package -DskipTests -B -pl spark-jobs --am

FROM docker.io/apache/spark:3.5.8-scala2.12-java17-ubuntu
LABEL org.opencontainers.image.source=https://github.com/Epitech-Bryan/homepedia
LABEL org.opencontainers.image.description="HomePedia Spark jobs — heavy DVF aggregations and comparable-sales matching"
LABEL org.opencontainers.image.licenses=MIT
USER root
COPY --from=build /app/spark-jobs/target/spark-jobs-*-spark.jar /opt/spark/jars/homepedia-spark-jobs.jar
USER spark
WORKDIR /opt/spark/work-dir
