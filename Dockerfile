FROM zalando/openjdk:8u66-b17-1-2

MAINTAINER Zalando SE

COPY target/mint-worker.jar /

CMD java $JAVA_OPTS $(java-dynamic-memory-opts) $(appdynamics-agent) -jar /mint-worker.jar

ADD target/scm-source.json /scm-source.json
