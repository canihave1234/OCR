FROM eclipse-temurin:17-jdk

RUN apt-get update && \
    apt-get install -y tesseract-ocr libtesseract-dev curl && \
    apt-get clean

WORKDIR /app

RUN mkdir -p lib && \
    curl -L -o lib/tess4j.jar https://repo1.maven.org/maven2/net/sourceforge/tess4j/tess4j/5.4.0/tess4j-5.4.0.jar && \
    curl -L -o lib/sqlite-jdbc.jar https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar

COPY . /app

RUN javac -encoding UTF-8 \
    -cp "lib/tess4j.jar:lib/sqlite-jdbc.jar" \
    -d out /app/src/server/ocrServer.java

CMD ["java", "-cp", "out:lib/tess4j.jar:lib/sqlite-jdbc.jar", "server.ocrServer"]

EXPOSE 8080
