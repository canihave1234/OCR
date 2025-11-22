FROM eclipse-temurin:17-jdk

RUN apt-get update && \
    apt-get install -y tesseract-ocr libtesseract-dev curl unzip && \
    apt-get clean

WORKDIR /app

RUN mkdir -p lib

RUN curl -L -o lib/tess4j.jar \
    https://repo1.maven.org/maven2/net/sourceforge/tess4j/tess4j/5.4.0/tess4j-5.4.0.jar

RUN curl -L -o lib/sqlite-jdbc.jar \
    https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar

COPY . .

RUN mkdir -p out && \
    javac -encoding UTF-8 -cp "lib/*" -d out src/server/ocrServer.java

CMD ["/bin/bash", "-c", "echo '=== 현재 위치 ===' && pwd && echo '=== out 폴더 ===' && ls -laR out/ && echo '=== lib 폴더 ===' && ls -la lib/ && echo '=== 실행 시도 ===' && java -cp out:lib/tess4j.jar:lib/sqlite-jdbc.jar server.ocrServer"]

EXPOSE 8080