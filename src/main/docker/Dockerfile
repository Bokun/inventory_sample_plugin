FROM amazoncorretto:25.0.4

VOLUME /tmp
ADD inventory_sample_plugin.jar app.jar
# Keep --sun-misc-unsafe-memory-access in sync with jdkCompatibilityJvmArgs in build.gradle
ENTRYPOINT ["java","--sun-misc-unsafe-memory-access=allow","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]
