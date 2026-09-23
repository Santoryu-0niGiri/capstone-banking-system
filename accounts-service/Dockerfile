FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY accounts-service/target/accounts-service-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
