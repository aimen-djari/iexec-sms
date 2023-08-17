FROM eclipse-temurin:11.0.20_8-jre-focal

ARG jar

RUN test -n "$jar"

RUN apt-get update \
    && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/*

COPY $jar /app/iexec-sms.jar

COPY src/main/resources/ssl-keystore-dev.p12 /app/ssl-keystore-dev.p12

ENTRYPOINT [ "/bin/sh", "-c", "java -jar /app/iexec-sms.jar" ]
