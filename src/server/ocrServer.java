package server;

import com.sun.net.httpserver.*;
import net.sourceforge.tess4j.Tesseract;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
        Class.forName("org.sqlite.JDBC");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        initDb();

        // Serve camera.html
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                byte[] html = Files.readAllBytes(Paths.get("camera.html"));
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);
                exchange.close();
            }
        });

        // /db (HTML)
        server.createContext("/db", exchange -> {
            try {
                String html = buildDbPage();
                byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });

        // /db-json
        server.createContext("/db-json", exchange -> {
            try {
                String json = getDbJson();
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            } catch (Exception e) {
                e.printStackTrace();
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });

        // /delete?id=N
        server.createContext("/delete", exchange -> {
            try {
                String q = exchange.getRequestURI().getQuery();
                if (q != null && q.startsWith("id=")) {
                    int id = Integer.parseInt(q.substring(3));
                    deleteById(id);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            exchange.getResponseHeaders().add("Location", "/db");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // /uploadBase64
        server.createContext("/uploadBase64", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                System.out.println("📥 Received upload");

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("📦 Body length: " + body.length());

                String base64 = extract(body, "image")
                        .replace("data:image/png;base64,", "")
                        .replace("data:image/jpeg;base64,", "")
                        .trim();

                String memo = extract(body, "memo");
                System.out.println("📝 Memo: " + memo);

                if (base64.isEmpty()) {
                    System.out.println("❌ No image data!");
                    String err = "{\"error\":\"No image\"}";
                    exchange.sendResponseHeaders(400, err.length());
                    exchange.getResponseBody().write(err.getBytes());
                    exchange.close();
                    return;
                }

                byte[] imgBytes = Base64.getDecoder().decode(base64);
                Path rawPath = Paths.get("/app/uploaded.png");
                Files.write(rawPath, imgBytes);
                System.out.println("📸 Saved image: " + imgBytes.length + " bytes");

                String result = runOCR(rawPath.toFile());
                System.out.println("🔍 OCR RESULT = " + result);

                try {
                    saveToDB(result, memo);
                    System.out.println("✅ Saved to DB!");
                } catch (Exception e) {
                    System.out.println("❌ DB Save failed!");
                    e.printStackTrace();
                }

                String response = "{\"result\":\"" + escapeJson(result) + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            }
        });

        server.start();
        System.out.println("🚀 Server running on port " + port);
    }

    // ============================================================
    // Utility Functions
    // ============================================================

    private static String getDbPath() {
        String base = System.getenv().getOrDefault("APP_HOME", "/app");
        File dir = new File(base);
        if (!dir.exists()) dir.mkdirs();
        return base + "/data.db";
    }

    private static void initDb() {
        try {
            String dbPath = getDbPath();
            System.out.println("Creating DB: " + dbPath);

            Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS deposits (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "text TEXT," +
                "memo TEXT," +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP);"
            );
            conn.close();
            System.out.println("✅ DB Ready!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extract(String json, String field) {
        try {
            String key = "\"" + field + "\":\"";
            int start = json.indexOf(key);
            if (start == -1) {
                key = "\"" + field + "\": \"";
                start = json.indexOf(key);
            }
            if (start == -1) {
                System.out.println("⚠ Field not found: " + field);
                return "";
            }
            start += key.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private static String getDbJson() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM deposits ORDER BY id DESC");

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        while (rs.next()) {
            if (!first) json.append(",");
            json.append("{")
                .append("\"id\":").append(rs.getInt("id")).append(",")
                .append("\"text\":\"").append(escapeJson(rs.getString("text") != null ? rs.getString("text") : "")).append("\",")
                .append("\"memo\":\"").append(escapeJson(rs.getString("memo") != null ? rs.getString("memo") : "")).append("\",")
                .append("\"created_at\":\"").append(rs.getString("created_at")).append("\"")
                .append("}");
            first = false;
        }
        json.append("]");
        rs.close();
        conn.close();
        return json.toString();
    }

    /** 이미지 전처리 - 개선 버전 */
    private static File preprocessImage(File input) throws Exception {
        BufferedImage img = ImageIO.read(input);
        int w = img.getWidth();
        int h = img.getHeight();

        // 2배 확대
        int newW = w * 2;
        int newH = h * 2;

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, 0, 0, newW, newH, null);
        g.dispose();

        // 이진화 (threshold)
        int threshold = 130;
        for (int y = 0; y < newH; y++) {
            for (int x = 0; x < newW; x++) {
                int pixel = scaled.getRGB(x, y) & 0xFF;
                int bw = pixel < threshold ? 0 : 255;
                scaled.setRGB(x, y, (bw << 16) | (bw << 8) | bw);
            }
        }

        File out = new File("/app/preprocessed.png");
        ImageIO.write(scaled, "png", out);
        System.out.println("📷 Preprocessed: " + newW + "x" + newH);
        return out;
    }

    /** OCR - 개선 버전 */
    private static String runOCR(File img) {
        try {
            File clean = preprocessImage(img);

            Tesseract t = new Tesseract();
            t.setDatapath("/usr/share/tesseract-ocr/5/tessdata/");
            t.setLanguage("eng");
            t.setPageSegMode(6);  // 단일 블록
            t.setOcrEngineMode(1);  // LSTM
            t.setTessVariable("tessedit_char_whitelist", 
                "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz.,$ ");

            String result = t.doOCR(clean).trim();
            System.out.println("🔍 OCR Raw: " + result);
            return result;

        } catch (Exception e) {
            System.out.println("⚠ OCR ERROR");
            e.printStackTrace();
            return "OCR_ERROR";
        }
    }

    private static void saveToDB(String text, String memo) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
        PreparedStatement ps = conn.prepareStatement("INSERT INTO deposits(text, memo) VALUES(?, ?)");
        ps.setString(1, text);
        ps.setString(2, memo);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    private static void deleteById(int id) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
        PreparedStatement ps = conn.prepareStatement("DELETE FROM deposits WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    private static String buildDbPage() throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM deposits ORDER BY id DESC");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>");
        html.append("body{background:#f4f9f4;font-family:Arial;margin:20px;}");
        html.append("table{border-collapse:collapse;width:95%;margin:auto;background:white;}");
        html.append("td,th{border:1px solid #ccc;padding:10px;text-align:center;}");
        html.append("th{background:#006644;color:white;}");
        html.append("h2{text-align:center;color:#006644;}");
        html.append("a.delete{color:red;text-decoration:none;font-weight:bold;}");
        html.append("</style></head><body>");
        html.append("<h2>📄OCR Deposit Records</h2>");
        html.append("<table><tr><th>ID</th><th>OCR</th><th>Memo</th><th>Time</th><th>Del</th></tr>");

        while (rs.next()) {
            html.append("<tr>")
                .append("<td>").append(rs.getInt("id")).append("</td>")
                .append("<td>").append(rs.getString("text") != null ? rs.getString("text") : "").append("</td>")
                .append("<td>").append(rs.getString("memo") != null ? rs.getString("memo") : "").append("</td>")
                .append("<td>").append(rs.getString("created_at")).append("</td>")
                .append("<td><a class='delete' href='/delete?id=").append(rs.getInt("id")).append("'>X</a></td>")
                .append("</tr>");
        }

        html.append("</table></body></html>");
        rs.close();
        conn.close();
        return html.toString();
    }
}