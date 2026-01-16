# ===================================================================
# ETAPA 1: Builder (Compilar el proyecto)
# ===================================================================
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compilamos y empaquetamos (sin correr tests de nuevo, ya pasaron en el pipeline)
RUN mvn clean package -DskipTests

# ===================================================================
# ETAPA 2: Extractor de Capas (Optimización Spring Boot)
# ===================================================================
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /app
# Traemos el JAR generado en la etapa 1
COPY --from=builder /app/target/*.jar app.jar
# Extraemos las capas (Librerías, Loader, Snapshot, Application)
RUN java -Djarmode=layertools -jar app.jar extract

# ===================================================================
# ETAPA 3: Runtime (Imagen Final Ligera)
# ===================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear usuario 'spring' para no correr como root (Seguridad)
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiamos las capas extraídas en orden (para optimizar caché)
# 1. Dependencias (Lo que menos cambia)
COPY --from=layers /app/dependencies/ ./
# 2. Loader de Spring
COPY --from=layers /app/spring-boot-loader/ ./
# 3. Dependencias Snapshot (si las hubiera)
COPY --from=layers /app/snapshot-dependencies/ ./
# 4. Tu código (Lo que más cambia)
COPY --from=layers /app/application/ ./

# Puerto expuesto
EXPOSE 8080

# Usamos el JarLauncher de Spring para arrancar optimizado
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]