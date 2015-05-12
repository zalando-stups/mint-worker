FROM zalando/openjdk:8u40-b09-4

MAINTAINER Zalando SE

COPY target/mint-worker.jar /

CMD java $(java-dynamic-memory-opts) -jar /mint-worker.jar

ADD /scm-source.json /scm-source.json
