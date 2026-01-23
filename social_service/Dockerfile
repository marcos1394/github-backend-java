# ===================================================================
# ETAPA 1: Builder (Compilar el proyecto)
# ===================================================================
# Aquí no cambia nada, usamos la imagen de Maven estándar
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ===================================================================
# ETAPA 2: Extractor de Capas
# ===================================================================
# CAMBIO 1: Usamos la imagen "Full" (Sin la palabra -alpine)
# Esto asegura compatibilidad binaria al extraer
FROM eclipse-temurin:21-jre AS layers
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

# ===================================================================
# ETAPA 3: Runtime (Imagen Final Estable para IA)
# ===================================================================
# CAMBIO 2: Usamos la imagen "Full" basada en Ubuntu/Debian
# Esto incluye las librerías 'glibc' que necesita Vertex AI/Netty
FROM eclipse-temurin:21-jre
WORKDIR /app

# CAMBIO 3: Sintaxis de usuario para Debian/Ubuntu
# En Alpine es 'addgroup/adduser', aquí es 'groupadd/useradd'
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Copiamos las capas (Igual que antes)
COPY --from=layers /app/dependencies/ ./
COPY --from=layers /app/spring-boot-loader/ ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/ ./

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]