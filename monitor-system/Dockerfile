# 第一阶段：编译
FROM maven:3.9-eclipse-temurin-17 AS build
COPY . /app
WORKDIR /app
RUN mvn clean package -DskipTests

# 第二阶段：运行
FROM eclipse-temurin:17-jre
COPY --from=build /app/target/*.jar /app.jar
# 生产环境运行指令
ENTRYPOINT ["java", "-jar", "/app.jar", "--spring.profiles.active=prod"]