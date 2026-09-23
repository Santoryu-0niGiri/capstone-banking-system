FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY login-service/target/login-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
