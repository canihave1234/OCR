# 📸 OCR Cheque Deposit Web App (Java + Tesseract)

A lightweight **mobile-style cheque deposit web application** built with **pure Java**, **HTML**, and **Tesseract OCR**.

Users can capture a cheque image using their device camera, extract text via OCR, optionally add a memo, and store records in a **SQLite** database.

> No frameworks.  
> No Maven. No Gradle.  
> Just raw Java, `HttpServer`, and Tesseract OCR.

---

##  Features

###  Mobile Camera Capture
- Fullscreen camera overlay
- Visual guide box for cheque alignment
- Countdown timer before image capture

###  OCR Processing
- Image preprocessing (grayscale + contrast enhancement)
- OCR using **Tesseract** (numbers & uppercase letters)

###  Memo Support
- Optional memo stored with each deposit

###  SQLite Database
- Automatic database creation
- Stores OCR result, memo, and timestamp

###  DB Viewer
- HTML table view (`/db`)
- JSON API (`/db-json`)
- Delete individual records

###  Docker-ready
- Runs on **Railway**, **Render**, or any Docker-compatible platform

---

##  Tech Stack

| Layer       | Technology |
|------------|------------|
| Backend    | Java 17 (`HttpServer`) |
| OCR        | Tesseract OCR + Tess4J |
| Database   | SQLite |
| Frontend  | HTML / CSS / Vanilla JS |
| Deployment| Docker (Railway compatible) |

---



##  How It Works

1. User opens `/`
2. Camera opens in fullscreen mode
3. Cheque is captured inside the guide box
4. Image is preprocessed
5. OCR extracts text
6. Result and memo are saved to SQLite
7. Records can be viewed via `/db` or `/db-json`

---

##  Endpoints

| URL | Description |
|----|------------|
| `/` | Main camera UI |
| `/uploadBase64` | Image + memo upload |
| `/db` | HTML database viewer |
| `/db-json` | JSON database API |
| `/delete?id=ID` | Delete a record |

---

##  Docker Build & Run

```bash
docker build -t ocr-app .
docker run -p 8080:8080 ocr-app


## Key Learning Points

Building a web server without frameworks

Handling camera input in mobile browsers

OCR preprocessing for improved accuracy

Managing SQLite in containerized environments

Debugging classpath and Docker runtime issues

## Notes

OCR accuracy depends heavily on:

Lighting conditions

Camera focus

Cheque alignment

This project focuses on engineering and system design concepts, not banking-grade security.


---

##  Project Structure

``` OCR/
├── camera.html # Main UI (mobile-style capture)
├── Dockerfile # Deployment configuration
├── src/
│ └── server/
│ └── ocrServer.java # Main Java server
├── lib/ # External JARs (tess4j, sqlite-jdbc)
├── data.db # SQLite DB (auto-generated) ```

