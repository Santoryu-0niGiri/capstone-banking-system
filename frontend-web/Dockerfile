FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY frontend-web/target/frontend-web-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]

