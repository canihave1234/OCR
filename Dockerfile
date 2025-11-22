FROM eclipse-temurin:17-jdk

RUN apt-get update && \
    apt-get install -y tesseract-ocr libtesseract-dev curl unzip && \
    apt-get clean

WORKDIR /app

# Prepare lib folder
RUN mkdir -p lib

# Download Tess4J JAR
RUN curl -L -o lib/tess4j.jar \
    https://repo1.maven.org/maven2/net/sourceforge/tess4j/tess4j/5.4.0/tess4j-5.4.0.jar

# Download SQLite JDBC
RUN curl -L -o lib/sqlite-jdbc.jar \
    https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar

# Copy entire project
COPY . .

# Compile Java → create .class files inside /app/out
RUN mkdir -p out && \
    javac -encoding UTF-8 -cp "lib/*" -d out src/server/ocrServer.java

# Run compiled class
CMD ["java", "-cp", "out:lib/*", "server.ocrServer"]

EXPOSE 8080