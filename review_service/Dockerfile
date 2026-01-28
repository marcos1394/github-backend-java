# ===================================================================
# ETAPA 1: Builder (Compilar el proyecto)
# ===================================================================
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compilamos saltando tests para agilizar el build en la nube
RUN mvn clean package -DskipTests

# ===================================================================
# ETAPA 2: Extractor de Capas (Optimización de Spring Boot)
# ===================================================================
FROM eclipse-temurin:21-jre AS layers
WORKDIR /app
# Tomamos el jar generado en la etapa anterior
COPY --from=builder /app/target/*.jar app.jar
# Extraemos las capas para optimizar el cache de Docker
RUN java -Djarmode=layertools -jar app.jar extract

# ===================================================================
# ETAPA 3: Runtime (Imagen Final Ligera y Segura)
# ===================================================================
FROM eclipse-temurin:21-jre
WORKDIR /app

# Crear usuario sin privilegios (Seguridad Enterprise)
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copiamos las capas extraídas en orden específico para optimizar arranque
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

# ⚠️ PUERTO ESPECÍFICO: Review Service corre en el 8085
EXPOSE 8085

# Usamos JarLauncher para arrancar usando las capas
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]