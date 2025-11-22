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

# 이 줄 추가: 컴파일 결과 확인
RUN echo "=== 컴파일된 파일 ===" && find out -type f && ls -la out/

CMD ["java", "-cp", "out:lib/*", "server.ocrServer"]

EXPOSE 8080
```

이렇게 푸시하고 **Build Logs**에서 `=== 컴파일된 파일 ===` 아래에 뭐가 나오는지 확인해주세요.

예상되는 정상 출력:
```
out/server/ocrServer.class