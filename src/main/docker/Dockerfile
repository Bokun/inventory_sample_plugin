FROM openjdk:8-jdk-alpine

#MAINTAINER Vidas Miliauskas <vidas@bokun.is>
#LABEL Description="Bókun Vendor On-boarding Metrics Service"

VOLUME /tmp
ADD inventory_sample_plugin.jar app.jar
#RUN bash -c 'touch /app.jar'
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
