# A Docker image for the FIT performers.
# Build from project root with:
#   docker build . --build-arg SDK=<sdk> -t performer
#
# Run with:
#   docker run -e LOG_LEVEL=DEBUG -p 8060:8060 performer

# Valid SDK values: java, scala, kotlin
ARG SDK=java

FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app
COPY . couchbase-jvm-clients/

WORKDIR /app/couchbase-jvm-clients
ARG MVN_FLAGS="--batch-mode --no-transfer-progress -Dcheckstyle.skip -Dmaven.test.skip -Dmaven.javadoc.skip"
RUN mvn $MVN_FLAGS -f protostellar/pom.xml clean install
RUN mvn $MVN_FLAGS -f core-io-deps/pom.xml clean install
RUN mvn $MVN_FLAGS -f tracing-opentelemetry-deps/pom.xml clean install

# As an optmization, pre-build common modules so Docker can cache this layer too.
#RUN mvn $MVN_FLAGS install -Pfit --projects core-io,core-fit-performer --also-make

# Defer declaring the ARG until first use, so the previous layers can be cached between SDKs
ARG SDK
RUN mvn $MVN_FLAGS package -Pfit --projects ${SDK}-fit-performer --also-make

# Multistage build to keep things small
FROM eclipse-temurin:21-jre-ubi9-minimal

WORKDIR /app
# Default level; override with docker run -e LOG_LEVEL=DEBUG
ENV LOG_LEVEL=INFO

ARG SDK
COPY --from=build /app/couchbase-jvm-clients/${SDK}-fit-performer/target/fit-performer-${SDK}-1.0-SNAPSHOT-jar-with-dependencies.jar performer.jar
ENTRYPOINT ["java", "-jar", "performer.jar"]
