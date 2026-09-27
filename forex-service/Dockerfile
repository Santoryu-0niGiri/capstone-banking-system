FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY forex-service/target/forex-service-*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
