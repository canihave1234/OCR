📸 OCR Cheque Deposit Web App (Java + Tesseract)

A lightweight mobile-style cheque deposit web application built with pure Java, HTML, and Tesseract OCR.
Users can capture a cheque image using their camera, extract text via OCR, add a memo, and store records in a SQLite database.

No frameworks.
No Maven. No Gradle.
Just raw Java, HttpServer, and Tesseract OCR.

Features
Mobile Camera Capture

Fullscreen camera overlay

Visual guide box for cheque alignment

Countdown timer before capture

OCR Processing

Image preprocessing (grayscale + contrast enhancement)

OCR using Tesseract (numbers & uppercase letters)

Memo Support

Optional memo stored with each deposit

SQLite Database

Automatic database creation

Stores OCR result, memo, and timestamp

DB Viewer

HTML table view (/db)

JSON API (/db-json)

Delete individual records

Docker-ready

Runs on Railway, Render, or any Docker-compatible platform

Tech Stack
Layer	Technology
Backend	Java 17 (HttpServer)
OCR	Tesseract OCR + Tess4J
Database	SQLite
Frontend	HTML / CSS / Vanilla JS
Deployment	Docker (Railway compatible)
Project Structure
OCR/
├── camera.html              # Main UI (mobile-style capture)
├── Dockerfile               # Deployment configuration
├── src/
│   └── server/
│       └── ocrServer.java   # Main Java server
├── lib/                     # External JARs (tess4j, sqlite-jdbc)
├── data.db                  # SQLite DB (auto-generated)

How It Works

User opens /

Camera opens in fullscreen

Cheque is captured inside the guide box

Image is preprocessed

OCR extracts text

Result and memo are saved to SQLite

Records can be viewed via /db or /db-json

Endpoints
URL	Description
/	Main camera UI
/uploadBase64	Image and memo upload
/db	HTML database viewer
/db-json	JSON database API
/delete?id=ID	Delete a record
Docker Build & Run
docker build -t ocr-app .
docker run -p 8080:8080 ocr-app


Then open:

http://localhost:8080

Key Learning Points

Building a web server without frameworks

Handling camera input in mobile browsers

OCR preprocessing for improved accuracy

Managing SQLite in containerized environments

Debugging classpath and Docker runtime issues

Notes

OCR accuracy depends heavily on:

Lighting conditions

Camera focus

Cheque alignment

This project focuses on engineering and system design concepts, not banking-grade security.
