FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY pom.xml .
RUN apt-get update -qq && apt-get install -y -qq maven > /dev/null && mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# OneScript для реального запуска кода 1С (https://oscript.io) — требует libicu для .NET
FROM eclipse-temurin:17-jre AS with-onescript
RUN apt-get update -qq \
  && apt-get install -y -qq curl unzip libicu-dev \
  && curl -sL -o /tmp/os.zip "https://github.com/EvilBeaver/OneScript/releases/download/v2.0.0/OneScript-2.0.0-linux-x64.zip" \
  && unzip -q /tmp/os.zip -d /tmp \
  && ONEDIR="$(dirname "$(find /tmp -maxdepth 3 -type f -name 'oscript' 2>/dev/null | head -1)")" \
  && ( [ -n "$ONEDIR" ] && [ -d "$ONEDIR" ] && mv "$ONEDIR" /opt/onescript ) || ( mv /tmp/OneScript-2.0.0-linux-x64 /opt/onescript 2>/dev/null || mv /tmp/OneScript-* /opt/onescript ) \
  && chmod -R a+rx /opt/onescript \
  && rm -f /tmp/os.zip \
  && apt-get remove -y curl unzip && apt-get autoremove -y -qq && rm -rf /var/lib/apt/lists/*
ENV PATH="/opt/onescript:$PATH"

FROM with-onescript
WORKDIR /app

RUN useradd -m -s /bin/bash appuser \
  && mkdir -p /app/run && chown -R appuser:appuser /app/run
USER appuser

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
