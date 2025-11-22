FROM ubuntu:22.04

# ========== Install Java + OCR engine ==========
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk tesseract-ocr libtesseract-dev wget unzip && \
    apt-get clean

WORKDIR /app

# ========== Download Tess4J ==========
RUN wget https://repo1.maven.org/maven2/net/sourceforge/tess4j/tess4j/5.4.0/tess4j-5.4.0.zip && \
    unzip tess4j-5.4.0.zip && \
    mv tess4j-5.4.0/lib ./lib && \
    rm -rf tess4j-5.4.0.zip tess4j-5.4.0

# ========== Copy source ==========
COPY src ./src
COPY camera.html .

RUN mkdir -p out

# ========== Compile ==========
RUN javac -encoding UTF-8 -cp "lib/*" -d out src/server/ocrServer.java

EXPOSE 8080

CMD ["bash", "-c", "java -cp 'out:lib/*' server.ocrServer"]
