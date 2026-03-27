# 生产阶段 - 运行阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 安装 curl 用于健康检查
RUN apk --no-cache add curl

# 复制本地构建好的 JAR 文件
# 注意：需要先执行 mvn clean package -DskipTests -pl lingshu-web -am
COPY backend/lingshu-web/target/lingshu-web-0.0.1-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8080

# JVM 参数优化
ENV JAVA_OPTS="-Xms512m -Xmx1g -XX:+UseG1GC"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
