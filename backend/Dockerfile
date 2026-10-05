FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN chmod +x mvnw && ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/api-1.0.0.jar app.jar
EXPOSE 18081
ENTRYPOINT ["java", "-jar", "app.jar"]
