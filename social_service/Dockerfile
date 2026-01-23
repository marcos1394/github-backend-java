# ===================================================================
# ETAPA 1: Builder (Compilar el proyecto)
# ===================================================================
# Usamos la imagen oficial de Maven con Java 21 (Temurin es la recomendada para prod)
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app

# Copiamos archivos de dependencias y código fuente
COPY pom.xml .
COPY src ./src

# Compilamos y empaquetamos
# -DskipTests: Ahorra tiempo en CI/CD (los tests ya deberían haber corrido en otro paso del pipeline)
RUN mvn clean package -DskipTests

# ===================================================================
# ETAPA 2: Extractor de Capas (Optimización Spring Boot)
# ===================================================================
FROM eclipse-temurin:21-jre-alpine AS layers
WORKDIR /app

# Traemos el JAR generado en la etapa 1
COPY --from=builder /app/target/*.jar app.jar

# Extraemos las capas del JAR usando el modo 'layertools' de Spring Boot
# Esto separa dependencias externas, loader y tu código de negocio
RUN java -Djarmode=layertools -jar app.jar extract

# ===================================================================
# ETAPA 3: Runtime (Imagen Final Ligera y Segura)
# ===================================================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# SEGURIDAD: Crear usuario 'spring' para no correr como root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiamos las capas extraídas en orden específico para optimizar la caché de Docker
# 1. Dependencias (Libs de Spring, Google Cloud, etc. - Rara vez cambian)
COPY --from=layers /app/dependencies/ ./
# 2. Loader de Spring Boot
COPY --from=layers /app/spring-boot-loader/ ./
# 3. Dependencias Snapshot (si las hubiera)
COPY --from=layers /app/snapshot-dependencies/ ./
# 4. Tu código (Application) - Esto es lo que cambia en cada deploy
COPY --from=layers /app/application/ ./

# Exponemos el puerto 8080 (Cloud Run inyecta PORT=8080 por defecto)
EXPOSE 8080

# Usamos el JarLauncher específico de Spring Boot 3.2+ / 4.x
# Nota: La ruta 'org.springframework.boot.loader.launch.JarLauncher' es la correcta para versiones nuevas.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]