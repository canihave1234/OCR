FROM eclipse-temurin:17-jdk

RUN apt-get update && \
    apt-get install -y tesseract-ocr libtesseract-dev curl && \
    apt-get clean

WORKDIR /workspace/OCR

RUN mkdir -p lib

# Tess4J direct JAR download 
RUN curl -L -o lib/tess4j.jar https://repo1.maven.org/maven2/net/sourceforge/tess4j/tess4j/4.5.5/tess4j-4.5.5.jar && \
    curl -L -o lib/jai-imageio-core.jar https://repo1.maven.org/maven2/com/github/jai-imageio/jai-imageio-core/1.4.0/jai-imageio-core-1.4.0.jar && \
    curl -L -o lib/jna.jar https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.12.1/jna-5.12.1.jar && \
    curl -L -o lib/jna-platform.jar https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/5.12.1/jna-platform-5.12.1.jar

# SQLite JDBC
RUN curl -L -o lib/sqlite-jdbc.jar \
  https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar

COPY . .

# Compile
RUN mkdir -p out && \
    javac -encoding UTF-8 -cp "lib/*" -d out src/server/ocrServer.java

# Run
CMD ["java", "-cp", "out:lib/*", "server.ocrServer"]

EXPOSE 8080