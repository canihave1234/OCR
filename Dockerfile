FROM eclipse-temurin:17-jdk

RUN apt-get update && \
    apt-get install -y tesseract-ocr libtesseract-dev curl unzip && \
    apt-get clean

WORKDIR /app

RUN mkdir -p lib

# Tess4J + 의존성들
RUN curl -L -o lib/tess4j.jar \
    https://repo1.maven.org/maven2/net/sourceforge/tess4j/tess4j/5.4.0/tess4j-5.4.0.jar

RUN curl -L -o lib/jai-imageio-core.jar \
    https://repo1.maven.org/maven2/com/github/jai-imageio/jai-imageio-core/1.4.0/jai-imageio-core-1.4.0.jar

RUN curl -L -o lib/jna.jar \
    https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.13.0/jna-5.13.0.jar

RUN curl -L -o lib/lept4j.jar \
    https://repo1.maven.org/maven2/net/sourceforge/lept4j/lept4j/1.18.0/lept4j-1.18.0.jar

# SQLite
RUN curl -L -o lib/sqlite-jdbc.jar \
    https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar

# SLF4J
RUN curl -L -o lib/slf4j-api.jar \
    https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar

RUN curl -L -o lib/slf4j-simple.jar \
    https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.9/slf4j-simple-2.0.9.jar

COPY . .

RUN mkdir -p out && \
    javac -encoding UTF-8 -cp "lib/*" -d out src/server/ocrServer.java

ENTRYPOINT ["/bin/bash", "-c", "java -cp out:lib/* server.ocrServer"]

EXPOSE 8080