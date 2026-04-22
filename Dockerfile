FROM gradle:8.14.4-jdk21-alpine AS build

WORKDIR /workspace

COPY youtube-atlas-backend/ ./

RUN gradle bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN apk add --no-cache postgresql-client

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
COPY sql /app/sql
COPY scripts/run-db-migrations.sh /app/scripts/run-db-migrations.sh
COPY scripts/start-backend.sh /app/scripts/start-backend.sh

RUN chmod +x /app/scripts/run-db-migrations.sh /app/scripts/start-backend.sh

ENV PORT=8080

EXPOSE 8080

ENTRYPOINT ["/app/scripts/start-backend.sh"]
