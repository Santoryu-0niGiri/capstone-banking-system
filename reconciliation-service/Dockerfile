FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY reconciliation-service/target/reconciliation-service-*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
