FROM zalando/openjdk:8u40-b09-4

MAINTAINER Zalando SE

COPY target/mint-storage.jar /

EXPOSE 8080
ENV HTTP_PORT=8080

CMD java $(java-dynamic-memory-opts) -jar /mint-storage.jar

ADD target/scm-source.json /scm-source.json
