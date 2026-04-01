# Build stage — Docker context must be repository root (see scripts/k8s-build-images.ps1).
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

# Shared library (com.example:CommonLib) required by ProductService
COPY CommonLib/pom.xml CommonLib/
COPY CommonLib/src CommonLib/src
RUN mvn -f CommonLib/pom.xml install -DskipTests -B

COPY ProductService/pom.xml ProductService/
RUN mvn -f ProductService/pom.xml dependency:go-offline -B

COPY ProductService/src ProductService/src
RUN mvn -f ProductService/pom.xml package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /workspace/ProductService/target/*.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "app.jar"]
