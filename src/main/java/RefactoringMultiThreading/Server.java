package RefactoringMultiThreading;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private static final List<String> VALID_PATHS = List.of(
            "/index.html", "/spring.svg", "/spring.png", "/resources.html",
            "/styles.css", "/app.js", "/links.html", "/forms.html",
            "/classic.html", "/events.html", "/events.js"
    );

    private static final int PORT = 8080;
    private static final int THREAD_POOL_SIZE = 64;
    private static final String PUBLIC_DIR = "public";

    private final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);
            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();
                threadPool.submit(() -> handleRequest(socket));
            }
        } catch (IOException e) {
            System.err.println("Ошибка при запуске сервера: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private void handleRequest(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }

            // Заголовок
            String path = parts[1];
            if (!VALID_PATHS.contains(path)) {
                sendErrorResponse(out, 404, "Not Found");
                return;
            }

            // Заголовок с public
            Path filePath = Path.of(PUBLIC_DIR, path);
            String mimeType = Files.probeContentType(filePath);

            if (path.equals("/classic.html")) {
                String template = Files.readString(filePath);
                String content = template.replace("{time}", LocalDateTime.now().toString());
                byte[] contentBytes = content.getBytes();
                sendResponse(out, 200, "OK", mimeType, contentBytes.length, contentBytes);
                return;
            }

            long length = Files.size(filePath);
            sendResponseHeader(out, 200, "OK", mimeType, length);
            Files.copy(filePath, out);

        } catch (IOException e) {
            // Логирование можно расширить, но для учебного проекта — тихо игнорируем
        }
    }

    private void sendErrorResponse(BufferedOutputStream out, int code, String message) {
        String statusLine = String.format("HTTP/1.1 %d %s\r\n", code, message);
        String response = statusLine +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        try {
            out.write(response.getBytes());
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private void sendResponse(BufferedOutputStream out, int code, String message, String mimeType, int contentLength, byte[] content) {
        String statusLine = String.format("HTTP/1.1 %d %s\r\n", code, message);
        String headers = statusLine +
                "Content-Type: " + (mimeType != null ? mimeType : "application/octet-stream") + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        try {
            out.write(headers.getBytes());
            out.write(content);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private void sendResponseHeader(BufferedOutputStream out, int code, String message, String mimeType, long contentLength) {
        String statusLine = String.format("HTTP/1.1 %d %s\r\n", code, message);
        String headers = statusLine +
                "Content-Type: " + (mimeType != null ? mimeType : "application/octet-stream") + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Connection: close\r\n" +
                "\r\n";
        try {
            out.write(headers.getBytes());
            out.flush();
        } catch (IOException ignored) {
        }
    }
}