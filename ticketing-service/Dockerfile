# ==============================================================================
# FlashTicket - Dockerfile cho Ticketing Service (Multi-stage Build tối ưu RAM)
# ==============================================================================

# Stage 1: Build JAR bằng Maven và OpenJDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper và pom.xml trước để tận dụng Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B || true

# Copy toàn bộ mã nguồn và biên dịch package
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Trích xuất Spring Boot layered JAR để tối ưu dung lượng và thời gian khởi động
RUN java -Djarmode=layertools -jar target/ticketing-service-*.jar extract

# Stage 2: Runtime Image siêu nhẹ bằng OpenJDK 21 JRE Alpine
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Cài đặt curl để phục vụ Healthcheck container
RUN apk add --no-cache curl tzdata && \
    cp /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime && \
    echo "Asia/Ho_Chi_Minh" > /etc/timezone

# Tạo user không đặc quyền (non-root) để đảm bảo an ninh Podman Rootless
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy các layer đã tách biệt từ Stage 1
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

EXPOSE 8080

# Cấu hình cờ JVM tối ưu cho VPS giới hạn RAM (~150MB):
# Sử dụng SerialGC hoặc G1GC nhẹ, giải phóng bộ nhớ chủ động về cho OS
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
