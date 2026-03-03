FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S fixengine && adduser -S fixengine -G fixengine

WORKDIR /opt/fix-engine

RUN mkdir -p logs data/fix-store data/fix-messages conf

COPY target/fix-engine-2.1.0.jar lib/fix-engine-2.1.0.jar
COPY src/main/resources/application.yaml conf/
COPY src/main/resources/logback-spring.xml conf/

RUN chown -R fixengine:fixengine /opt/fix-engine

USER fixengine

EXPOSE 8080 5701 9876 9877 9878

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-Xmx4g", "-Xms2g", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-Dspring.config.location=/opt/fix-engine/conf/application.yaml", \
  "-Dlogging.config=/opt/fix-engine/conf/logback-spring.xml", \
  "-jar", "lib/fix-engine-2.1.0.jar"]
