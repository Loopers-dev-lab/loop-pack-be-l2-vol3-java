FROM eclipse-temurin:21-jre

WORKDIR /app
COPY pg-simulator.jar app.jar
COPY application-docker.yml config/application-docker.yml

ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV TZ=Asia/Seoul

EXPOSE 8082 8083

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=local,docker --spring.config.additional-location=file:./config/"]
