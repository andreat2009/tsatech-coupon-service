FROM image-registry.openshift-image-registry.svc:5000/openshift/java-runtime:openjdk-17-ubi8
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} /app/app.jar
EXPOSE 8094
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
