

FROM eclipse-temurin:17-jdk

RUN apt-get update && \
    apt-get install -y tesseract-ocr libtesseract-dev curl unzip && \
    apt-get clean

# GitHub Repo 이름과 동일하게 WORKDIR 설정
WORKDIR /workspace/OCR

# Create lib folder
RUN mkdir -p lib

# Download Tess4J
RUN curl -L -o tess4j.zip https://downloads.sourceforge.net/project/tess4j/tess4j/5.4.0/tess4j-5.4.0.zip && \
    unzip tess4j.zip && \
    cp -r tess4j-5.4.0/lib/* lib/ && \
    rm -rf tess4j.zip tess4j-5.4.0

# SQLite JDBC
RUN curl -L -o lib/sqlite-jdbc.jar \
    https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.42.0.0/sqlite-jdbc-3.42.0.0.jar

# Copy Project Files
COPY . /workspace/OCR

# Compile
RUN mkdir -p out && \
    javac -encoding UTF-8 -cp "lib/*" -d out src/server/ocrServer.java

# Run
CMD ["java", "-cp", "out:lib/*", "server.ocrServer"]

EXPOSE 8080