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

# 모든 Java 파일 컴파일
RUN mkdir -p out && \
    find src -name "*.java" > sources.txt && \
    javac -encoding UTF-8 -cp "lib/*" -d out @sources.txt

# 디버깅 (문제 해결 후 제거)
RUN echo "=== 컴파일된 클래스 파일 ===" && find out -name "*.class"

CMD ["java", "-cp", "out:lib/*", "server.ocrServer"]

EXPOSE 8080