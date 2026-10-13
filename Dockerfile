FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package \
    && JAR=$(ls target/dibidani-backend-*.jar | grep -v '\-plain\.jar$' | head -n1) \
    && cp "$JAR" /tmp/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache wget \
    && addgroup -S dibidani && adduser -S dibidani -G dibidani \
    && mkdir -p /app/uploads && chown -R dibidani:dibidani /app
WORKDIR /app
COPY --from=build /tmp/app.jar app.jar
USER dibidani
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=20s --timeout=5s --start-period=50s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
