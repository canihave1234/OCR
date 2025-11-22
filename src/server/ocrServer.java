 package server;

import com.sun.net.httpserver.*;
import net.sourceforge.tess4j.Tesseract;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Base64;

import javax.imageio.ImageIO;

public class ocrServer {

    public static void main(String[] args) throws Exception {

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // -----------------------------
        // 1) DB 초기 생성 (Railway fix)
        // -----------------------------
        initDb();

        // -----------------------------
        // 2) Serve camera.html
        // -----------------------------
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                byte[] html = Files.readAllBytes(Paths.get("camera.html"));
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);
                exchange.close();
            }
        });

        // -----------------------------
        // 3) /db (HTML 조회)
        // -----------------------------
        server.createContext("/db", exchange -> {
            try {
                String html = buildDbPage();
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, html.getBytes().length);
                exchange.getResponseBody().write(html.getBytes());
                exchange.close();
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });

        // -----------------------------
        // 4) /db-json (JSON API)
        // -----------------------------
        server.createContext("/db-json", exchange -> {
            try {
                String json = getDbJson();
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, json.length());
                exchange.getResponseBody().write(json.getBytes());
                exchange.close();
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });

        // -----------------------------
        // 5) delete?id=3
        // -----------------------------
        server.createContext("/delete", exchange -> {
            try {
                String q = exchange.getRequestURI().getQuery();
                if (q != null && q.startsWith("id=")) {
                    int id = Integer.parseInt(q.substring(3));
                    deleteById(id);
                }
            } catch (Exception ignored) {}

            exchange.getResponseHeaders().add("Location", "/db");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // -----------------------------
        // 6) Upload Base64 Image
        // -----------------------------
        server.createContext("/uploadBase64", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {

                System.out.println("📥 Received upload");

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                String base64 = extract(body, "image")
                        .replace("data:image/png;base64,", "")
                        .trim();

                String memo = extract(body, "memo");

                // Save image
                byte[] imgBytes = Base64.getDecoder().decode(base64);
                Path rawPath = Paths.get("/app/uploaded.png");
                Files.write(rawPath, imgBytes);

                System.out.println("📸 Saved image!");

                // OCR
                String result = runOCR(rawPath.toFile());
                System.out.println("🔍 OCR RESULT = " + result);

                // Save DB
                try {
					saveToDB(result, memo);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

                // Response
                String response = "{\"result\":\"" + result + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            }
        });

        server.start();
        System.out.println("🚀 Server running!");
    }

    // ============================================================
    // Utility Functions
    // ============================================================

    private static String getDbPath() {
        return "/app/data.db"; // Railway 고정 경로
    }

    /** DB 자동 생성 */
    private static void initDb() {
        try {
            String dbPath = getDbPath();
            File dbFile = new File(dbPath);

            if (!dbFile.exists()) {
                System.out.println("📦 Creating DB file: " + dbPath);
                Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                conn.createStatement().execute(
                        "CREATE TABLE IF NOT EXISTS deposits (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "text TEXT," +
                                "memo TEXT," +
                                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)"
                );
                conn.close();
            }

        } catch (Exception e) {
            System.out.println("❌ DB init error");
            e.printStackTrace();
        }
    }

    /** JSON field extract */
    private static String extract(String json, String field) {
        try {
            String key = "\"" + field + "\":\"";
            int start = json.indexOf(key);
            if (start == -1) return "";
            start += key.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    /** DB → JSON */
    private static String getDbJson() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM deposits ORDER BY id DESC");

        StringBuilder json = new StringBuilder("[");
        boolean first = true;

        while (rs.next()) {
            if (!first) json.append(",");
            json.append("{")
                    .append("\"id\":").append(rs.getInt("id")).append(",")
                    .append("\"text\":\"").append(rs.getString("text")).append("\",")
                    .append("\"memo\":\"").append(rs.getString("memo")).append("\",")
                    .append("\"created_at\":\"").append(rs.getString("created_at")).append("\"")
                    .append("}");
            first = false;
        }

        json.append("]");

        rs.close();
        conn.close();
        return json.toString();
    }

    /** Image Preprocess */
    private static File preprocessImage(File input) throws Exception {
        BufferedImage img = ImageIO.read(input);

        int w = img.getWidth();
        int h = img.getHeight();

        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        // Contrast boost
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = gray.getRGB(x, y) & 0xFF;
                int enhanced = Math.min(255, pixel * 2);
                int rgb = (enhanced << 16) | (enhanced << 8) | enhanced;
                gray.setRGB(x, y, rgb);
            }
        }

        File out = new File("/app/preprocessed.png");
        ImageIO.write(gray, "png", out);

        return out;
    }

    /** OCR */
    private static String runOCR(File img) {
        try {
            File clean = preprocessImage(img);

            Tesseract t = new Tesseract();
            t.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata/");
            t.setLanguage("eng");
            t.setPageSegMode(7);
            t.setTessVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ");

            return t.doOCR(clean).trim();

        } catch (Exception e) {
            System.out.println("⚠ OCR ERROR");
            return "OCR_ERROR";
        }
    }

    /** Insert into DB */
    private static void saveToDB(String text, String memo) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());

        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO deposits(text, memo) VALUES(?, ?)"
        );
        ps.setString(1, text);
        ps.setString(2, memo);
        ps.executeUpdate();

        ps.close();
        conn.close();
    }

    /** Delete one row */
    private static void deleteById(int id) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
        PreparedStatement ps = conn.prepareStatement("DELETE FROM deposits WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    /** HTML DB Page */
    private static String buildDbPage() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM deposits ORDER BY id DESC");

        StringBuilder html = new StringBuilder();

        html.append("<html><head><meta charset='UTF-8'><style>");
        html.append("body{background:#f4f9f4;font-family:Arial;margin:20px;}");
        html.append("table{border-collapse:collapse;width:95%;margin:auto;background:white;}");
        html.append("td,th{border:1px solid #ccc;padding:10px;text-align:center;}");
        html.append("th{background:#006644;color:white;}");
        html.append("h2{text-align:center;color:#006644;}");
        html.append("a.delete{color:red;text-decoration:none;font-weight:bold;}");
        html.append("a.delete:hover{color:#aa0000;}");
        html.append("</style></head><body>");

        html.append("<h2>📄 OCR Deposit Records</h2>");
        html.append("<table>");
        html.append("<tr><th>ID</th><th>OCR</th><th>Memo</th><th>Timestamp</th><th></th></tr>");

        while (rs.next()) {
            html.append("<tr>")
                    .append("<td>").append(rs.getInt("id")).append("</td>")
                    .append("<td>").append(rs.getString("text")).append("</td>")
                    .append("<td>").append(rs.getString("memo")).append("</td>")
                    .append("<td>").append(rs.getString("created_at")).append("</td>")
                    .append("<td><a class='delete' href='/delete?id=")
                    .append(rs.getInt("id"))
                    .append("'>X</a></td>")
                    .append("</tr>");
        }

        html.append("</table></body></html>");

        rs.close();
        conn.close();

        return html.toString();
    }
}
