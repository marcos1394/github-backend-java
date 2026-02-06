# ===================================================================
# ETAPA 1: Builder (Compilar el proyecto)
# ===================================================================
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compilamos saltando tests para agilidad
RUN mvn clean package -DskipTests

# ===================================================================
# ETAPA 2: Extractor de Capas (Optimización de Spring Boot)
# ===================================================================
FROM eclipse-temurin:21-jre AS layers
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
# Extraemos las capas
RUN java -Djarmode=layertools -jar app.jar extract

# ===================================================================
# ETAPA 3: Runtime (Imagen Final Ligera y Compatible con GCP)
# ===================================================================
FROM eclipse-temurin:21-jre
WORKDIR /app

# Crear usuario sin privilegios
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copiamos capas
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

# ⚠️ PUERTO ONBOARDING: 8081
EXPOSE 8081

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]