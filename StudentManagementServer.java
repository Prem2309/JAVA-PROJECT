import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import com.sun.net.httpserver.*;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class StudentManagementServer {

    static java.util.List<Map<String, String>> students = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);

        server.createContext("/", new StaticFileHandler());
        server.createContext("/login", new LoginHandler());
        server.createContext("/add-student", new AddStudentHandler());
        server.createContext("/list-students", new ListStudentsHandler());
        server.createContext("/remove-student", new RemoveStudentHandler());
        server.createContext("/download-pdf", new DownloadPDFHandler());
        server.createContext("/students", new StudentDataHandler());

        server.setExecutor(null);
        System.out.println("Server started at http://localhost:8000");
        server.start();
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/login.html";

            File file = new File("web" + path);
            if (!file.exists()) {
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
                StringBuilder buf = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) buf.append(line);

                Map<String, String> formData = Arrays.stream(buf.toString().split("&"))
                        .map(pair -> pair.split("="))
                        .filter(pair -> pair.length == 2)
                        .collect(Collectors.toMap(
                                pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                                pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                        ));

                if ("admin".equals(formData.get("username")) && "admin".equals(formData.get("password"))) {
                    exchange.getResponseHeaders().add("Location", "/home.html");
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    String response = "<script>alert('Login Failed!'); window.location.href='/login.html';</script>";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            }
        }
    }

    static class AddStudentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);

                Map<String, String> student = Arrays.stream(sb.toString().split("&"))
                        .map(pair -> pair.split("="))
                        .filter(pair -> pair.length == 2)
                        .collect(Collectors.toMap(
                                pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                                pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                        ));

                students.add(student);
                String response = "<script>alert('Student added!'); window.location.href='/home.html';</script>";
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            }
        }
    }

    static class ListStudentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><title>List Students</title></head><body>");
            html.append("<h2>Student List</h2><ul>");

            for (Map<String, String> student : students) {
                html.append("<li>").append(student.get("name")).append(" (")
                        .append(student.get("roll")).append(") - ")
                        .append(student.get("course")).append("</li>");
            }

            html.append("</ul><br><a href='/home.html'>Back</a></body></html>");
            byte[] responseBytes = html.toString().getBytes();
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.getResponseBody().close();
        }
    }

    static class RemoveStudentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);

                String rollToRemove = Arrays.stream(sb.toString().split("&"))
                        .map(pair -> pair.split("="))
                        .filter(pair -> pair.length == 2 && "roll".equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8)))
                        .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                        .findFirst().orElse("");

                students.removeIf(student -> student.get("roll").equals(rollToRemove));

                String response = "<script>alert('Student removed!'); window.location.href='/home.html';</script>";
                exchange.sendResponseHeaders(200, response.length());
                exchange.getResponseBody().write(response.getBytes());
                exchange.getResponseBody().close();
            }
        }
    }

    static class DownloadPDFHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                Document document = new Document();
                File pdfFile = new File("students.pdf");
                PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
                document.open();

                Paragraph title = new Paragraph("Student List", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
                document.add(title);
                document.add(new Paragraph(" "));

                com.itextpdf.text.List pdfList = new com.itextpdf.text.List();
                for (Map<String, String> student : students) {
                    String entry = student.get("name") + " (" + student.get("roll") + ") - " + student.get("course");
                    pdfList.add(new ListItem(entry));
                }

                document.add(pdfList);
                document.close();

                byte[] pdfData = Files.readAllBytes(pdfFile.toPath());
                exchange.getResponseHeaders().add("Content-Type", "application/pdf");
                exchange.sendResponseHeaders(200, pdfData.length);
                OutputStream os = exchange.getResponseBody();
                os.write(pdfData);
                os.close();
            } catch (Exception e) {
                e.printStackTrace();
                String error = "Error generating PDF";
                exchange.sendResponseHeaders(500, error.length());
                exchange.getResponseBody().write(error.getBytes());
                exchange.getResponseBody().close();
            }
        }
    }

    static class StudentDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String json = "[" + students.stream().map(student -> {
                return String.format("{\"name\":\"%s\",\"roll\":\"%s\",\"course\":\"%s\"}",
                        student.get("name"), student.get("roll"), student.get("course"));
            }).collect(Collectors.joining(",")) + "]";

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, json.getBytes().length);
            exchange.getResponseBody().write(json.getBytes());
            exchange.getResponseBody().close();
        }
    }
}
