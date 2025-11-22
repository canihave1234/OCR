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

        System.out.println("🚀 Server starting on port: " + port);

        // ⭐ 서버 시작 시 DB 파일 없으면 바로 생성
        initDB();

        /* ------------------------ 1) Serve camera.html ------------------------ */
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                byte[] html = Files.readAllBytes(Paths.get("camera.html"));
                exchange.getResponseHeaders().add("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);
                exchange.close();
            }
        });

        /* ------------------------ 2) DB View Page ------------------------ */
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

        /* ------------------------ 3) DB JSON ------------------------ */
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

        /* ------------------------ 4) Delete Row ------------------------ */
        server.createContext("/delete", exchange -> {
            try {
                String q = exchange.getRequestURI().getQuery(); // id=3
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

        /* ------------------------ 5) Upload Base64 + memo ------------------------ */
        server.createContext("/uploadBase64", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                String base64 = extract(body, "image")
                        .replace("data:image/png;base64,", "")
                        .trim();

                String memo = extract(body, "memo");

                byte[] imgBytes = Base64.getDecoder().decode(base64);

                Files.write(Paths.get(getAppPath() + "/uploaded.png"), imgBytes);

                System.out.println("📸 Saved image!");

                // OCR
                String result = runOCR(new File(getAppPath() + "/uploaded.png"));
                System.out.println("🔍 OCR result: " + result);

                // Save DB
                saveToDB(result, memo);

                // Response
                String response = "{\"result\":\"" + result + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.close();
            }
        });

        server.start();
        System.out.println("🎉 Server running!");
    }

    /* =======================================================================
                               Utility Functions
    ======================================================================== */

    /** Local vs Server path 자동 구분 */
    private static String getAppPath() {
        String env = System.getenv("RAILWAY_ENVIRONMENT");
        if (env != null && env.length() > 0) return "/app"; // 서버
        return "."; // 로컬
    }

    private static String getDbPath() {
        String env = System.getenv("RAILWAY_ENVIRONMENT");
        if (env != null && env.length() > 0) return "/app/data.db";
        return "data.db";
    }

    /** 서버 시작 시 DB 생성 */
    private static void initDB() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath())) {

            String sql =
                    "CREATE TABLE IF NOT EXISTS deposits (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "text TEXT," +
                            "memo TEXT," +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                            ");";

            conn.createStatement().execute(sql);

            System.out.println("📁 DB initialized: " + getDbPath());

        } catch (Exception e) {
            System.out.println("❌ DB init error");
        }
    }

    /** JSON Extractor */
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

    /** OCR Preprocessing (contrast boost) */
    private static File preprocessImage(File input) throws Exception {
        BufferedImage img = ImageIO.read(input);

        int w = img.getWidth();
        int h = img.getHeight();

        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = gray.getRGB(x, y) & 0xFF;
                int enhanced = Math.min(255, Math.max(0, pixel * 2));
                int rgb = (enhanced << 16) | (enhanced << 8) | enhanced;
                gray.setRGB(x, y, rgb);
            }
        }

        File out = new File(getAppPath() + "/preprocessed.png");
        ImageIO.write(gray, "png", out);
        return out;
    }

    /** OCR Logic */
    private static String runOCR(File img) {
        try {
            File clean = preprocessImage(img);

            Tesseract t = new Tesseract();
            t.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata");
            t.setLanguage("eng");
            t.setPageSegMode(7);
            t.setTessVariable("tessedit_char_whitelist", "0123456789");

            return t.doOCR(clean).trim();

        } catch (Exception e) {
            return "OCR_ERROR";
        }
    }

    /** Insert to DB */
    private static void saveToDB(String text, String memo) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath())) {

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO deposits(text, memo) VALUES(?, ?)"
            );
            ps.setString(1, text);
            ps.setString(2, memo);
            ps.executeUpdate();

            System.out.println("💾 Saved to DB");

        } catch (Exception e) {
            System.out.println("❌ DB save error");
        }
    }

    /** Delete */
    private static void deleteById(int id) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
        PreparedStatement ps = conn.prepareStatement("DELETE FROM deposits WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    /** Convert DB → JSON */
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


    /** DB HTML Page */
    private static String buildDbPage() throws Exception {
    	  System.out.println("🔥 buildDbPage() called !!!!");
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + getDbPath());
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM deposits ORDER BY id DESC");

        StringBuilder html = new StringBuilder();


        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");
        html.append("body { font-family: Arial; margin:20px; background:#f4f9f4; }");
        html.append("table { border-collapse: collapse; width:95%; margin:auto; background:white; }");
        html.append("td,th { border:1px solid #ccc; padding:10px; text-align:center; }");
        html.append("th { background:#006644; color:white; }");
        html.append("h2 { text-align:center; color:#006644; }");
        html.append("a.delete { color:red; font-weight:bold; text-decoration:none; }");
        html.append("a.delete:hover { color:#b30000; }");
        html.append("</style>");
        html.append("</head><body>");

        html.append("<h2>📄 OCR Deposit Records</h2>");
        html.append("<table>");
        html.append("<tr><th>ID</th><th>OCR</th><th>Memo</th><th>Timestamp</th><th>Delete</th></tr>");

        while (rs.next()) {
            html.append("<tr>")
                .append("<td>").append(rs.getInt("id")).append("</td>")
                .append("<td>").append(rs.getString("text")).append("</td>")
                .append("<td>").append(rs.getString("memo")).append("</td>")
                .append("<td>").append(rs.getString("created_at")).append("</td>")
                .append("<td><a class='delete' href='/delete?id=")
                .append(rs.getInt("id"))
                .append("'>❌</a></td>")
                .append("</tr>");
        }

        html.append("</table></body></html>");

        rs.close();
        conn.close();

        return html.toString();
    }
}
