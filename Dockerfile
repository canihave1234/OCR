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

# 소스 파일 내용 확인
RUN echo "=== 소스 파일 첫 10줄 ===" && head -10 src/server/ocrServer.java

# 컴파일 (verbose 모드)
RUN mkdir -p out && \
    javac -verbose -encoding UTF-8 -cp "lib/*" -d out src/server/ocrServer.java 2>&1 | tail -20

# 결과 확인
RUN echo "=== out 폴더 전체 ===" && ls -laR out/

CMD ["java", "-cp", "out:lib/*", "server.ocrServer"]

EXPOSE 8080