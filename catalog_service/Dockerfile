# ===================================================================
# ETAPA 1: Builder (Compilar el proyecto)
# ===================================================================
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compilamos saltando tests para agilidad en el build de Docker
RUN mvn clean package -DskipTests

# ===================================================================
# ETAPA 2: Extractor de Capas (Optimización de Spring Boot)
# ===================================================================
FROM eclipse-temurin:21-jre AS layers
WORKDIR /app
# Copiamos el JAR generado en la etapa anterior
COPY --from=builder /app/target/*.jar app.jar
# Extraemos las capas para aprovechar el caché de Docker
RUN java -Djarmode=layertools -jar app.jar extract

# ===================================================================
# ETAPA 3: Runtime (Imagen Final Ligera y Segura)
# ===================================================================
FROM eclipse-temurin:21-jre
WORKDIR /app

# Crear usuario sin privilegios por seguridad (Best Practice)
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copiamos las capas extraídas (el orden importa para el caché)
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

# ⚠️ PUERTO CATALOG: 8082
# Asegúrate de que tu application.properties tenga server.port=8082
EXPOSE 8082

# Lanzador optimizado de Spring Boot
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]