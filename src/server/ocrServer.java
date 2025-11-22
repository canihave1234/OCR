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

//        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
    	int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    	HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);


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
     

        
        /* add function*/
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


        /* ------------------------ 3) Delete Row ------------------------ */
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

        /* ------------------------ 4) Upload Base64 + memo ------------------------ */
        server.createContext("/uploadBase64", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {

                System.out.println("Received upload!");

                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

                // safer JSON extraction
                String base64 = extract(body, "image")
                        .replace("data:image/png;base64,", "")
                        .trim();

                String memo = extract(body, "memo");

                // Save image
                byte[] imgBytes = Base64.getDecoder().decode(base64);
               // Path path = Paths.get("uploaded.png");
                Path path = Paths.get("/app/uploaded.png");
                Files.write(path, imgBytes);

                System.out.println("Saved: " + path.toAbsolutePath());

                // OCR
                String result = runOCR(path.toFile());
                System.out.println("OCR RESULT = " + result);

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
        System.out.println("Server running on http://localhost:8080/");
    }

    /* =======================================================================
                               Utility Functions
    ======================================================================== */

    private static String getDbJson() throws Exception {
      //  Connection conn = DriverManager.getConnection("jdbc:sqlite:data.db");
    	Connection conn = DriverManager.getConnection("jdbc:sqlite:/app/data.db");
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

    
    
    
    /** Extract a JSON field manually */
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
    
    
    /** add function */
    private static File preprocessImage(File input) throws Exception {
        BufferedImage img = ImageIO.read(input);

        int w = img.getWidth();
        int h = img.getHeight();

        // Grayscale + contrast boost
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        // Contrast stretching (간단한 histogram 방식)
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = gray.getRGB(x, y) & 0xFF;
                int enhanced = Math.min(255, Math.max(0, pixel * 2));
                int rgb = (enhanced << 16) | (enhanced << 8) | enhanced;
                gray.setRGB(x, y, rgb);
            }
        }

        // Save enhanced file
        File out = new File("preprocessed.png");
        ImageIO.write(gray, "png", out);

        return out;
    }


    /** OCR Logic */
    private static String runOCR(File img) {
        try {
            System.setProperty("jna.library.path", "/usr/local/lib:/usr/local/Cellar/tesseract/5.5.1/lib");

            File clean = preprocessImage(img);
            
            Tesseract t = new Tesseract();  
            t.setDatapath("/usr/local/share/tessdata");
            t.setLanguage("eng");
            t.setPageSegMode(7);
            t.setTessVariable("tessedit_char_whitelist", "0123456789");

            return t.doOCR(img).trim();

        } catch (Exception e) {
            System.out.println("⚠ OCR ERROR");
            return "OCR_ERROR";
        }
    }

    /** Create & Insert SQLite */
    private static void saveToDB(String text, String memo) {
        try {
            //Connection conn = DriverManager.getConnection("jdbc:sqlite:data.db");
        	Connection conn = DriverManager.getConnection("jdbc:sqlite:/app/data.db");


            // Re-create table always safely (id, text, memo, created_at)
            String createSQL =
                    "CREATE TABLE IF NOT EXISTS deposits (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                            "text TEXT," +
                            "memo TEXT," +
                            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                            ");";
            conn.createStatement().execute(createSQL);

            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO deposits(text, memo) VALUES(?, ?)"
            );
            ps.setString(1, text);
            ps.setString(2, memo);
            ps.executeUpdate();

            System.out.println("DB Saved!");

            ps.close();
            conn.close();

        } catch (Exception e) {
            System.out.println("⚠ DB ERROR");
            e.printStackTrace();
        }
    }

    private static void deleteById(int id) throws Exception {
      //  Connection conn = DriverManager.getConnection("jdbc:sqlite:data.db");
    	Connection conn = DriverManager.getConnection("jdbc:sqlite:/app/data.db");
        PreparedStatement ps = conn.prepareStatement("DELETE FROM deposits WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
        conn.close();
    }

    /** Build DB view page */
    private static String buildDbPage() throws Exception {

        //Connection conn = DriverManager.getConnection("jdbc:sqlite:data.db");
    	Connection conn = DriverManager.getConnection("jdbc:sqlite:/app/data.db");
        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM deposits ORDER BY id DESC");

        StringBuilder html = new StringBuilder();
        html.append("<html><head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<style>");
        html.append("table{border-collapse:collapse;width:90%;margin:auto;}");
        html.append("td,th{border:1px solid #888;padding:8px;text-align:center;}");
        html.append("h2{text-align:center;}");
        html.append("a.delete{color:red;font-weight:bold;}");
        html.append("</style>");
        html.append("</head><body>");
        html.append("<h2>OCR Records</h2>");
        html.append("<table>");
        html.append("<tr><th>ID</th><th>OCR Text</th><th>Memo</th><th>Timestamp</th><th>Delete</th></tr>");

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
