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
    // Примитивная "таблица маршрутизации" (Routing).
    // В Spring это делают аннотации @GetMapping, @PostMapping и т.д.
    private static final List<String> VALID_PATHS = List.of(
            "/index.html", "/spring.svg", "/spring.png", "/resources.html",
            "/styles.css", "/app.js", "/links.html", "/forms.html",
            "/classic.html", "/events.html", "/events.js"
    );

    private static final int PORT = 8080;
    // Пул потоков. В реальном мире этим занимается встроенный в Spring сервер (Tomcat/Undertow).
    // Он нужен, чтобы обрабатывать несколько запросов одновременно (асинхронно),
    // не блокируя основной поток сервера.
    private static final int THREAD_POOL_SIZE = 64;
    private static final String PUBLIC_DIR = "public";

    private final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);
            // Бесконечный цикл: сервер постоянно "слушает" порт (Listening)
            while (!Thread.currentThread().isInterrupted()) {
                // Блокирующий вызов: программа ждет, пока клиент (браузер/Postman)
                // не установит TCP-соединение (Three-way handshake из лекции).
                Socket socket = serverSocket.accept();
                // Как только соединение есть, отдаем его обработку свободному потоку из пула,
                // чтобы главный поток мог сразу же принять следующего клиента.
                threadPool.submit(() -> handleRequest(socket));
            }
        } catch (IOException e) {
            System.err.println("Ошибка при запуске сервера: " + e.getMessage());
        } finally {
            threadPool.shutdown();
        }
    }

    private void handleRequest(Socket socket) {
        // try-with-resources гарантирует, что потоки и сокет будут закрыты после обработки.
        // Это реализация принципа "Stateless" (состояние не сохраняется):
        // после ответа соединение закрывается (или возвращается в пул).
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())) {

            // 1. Читаем ПЕРВУЮ строку запроса (Request Line)
            // Пример: "GET /index.html HTTP/1.1"
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            // Разбиваем строку по пробелам. Должно быть ровно 3 части:
            // parts[0] = Метод (GET, POST...)
            // parts[1] = Путь (URL)
            // parts[2] = Версия протокола (HTTP/1.1)
            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                // Если формат нарушен, отправляем 400 Bad Request (ошибка клиента)
                sendErrorResponse(out, 400, "Bad Request");
                return;
            }

            // Заголовок
            // 2. Извлекаем путь (URL)
            String path = parts[1];

            // Примитивная проверка маршрутизации.
            // Если пути нет в списке, отправляем 404 Not Found.
            if (!VALID_PATHS.contains(path)) {
                sendErrorResponse(out, 404, "Not Found");
                return;
            }

            // Заголовок с public
            // 3. Формируем путь к файлу на диске
            Path filePath = Path.of(PUBLIC_DIR, path);
            // Определяем MIME-тип (Content-Type) автоматически по расширению файла.
            // Например, для .css это будет "text/css", для .html - "text/html".
            // В Spring это делает HttpMessageConverter автоматически.
            String mimeType = Files.probeContentType(filePath);
            // 4. Особая логика для одного файла (примитивный шаблонизатор, как Thymeleaf)
            if (path.equals("/classic.html")) {
                String template = Files.readString(filePath);
                // Динамическая подстановка данных (как модель в Spring MVC)
                String content = template.replace("{time}", LocalDateTime.now().toString());
                byte[] contentBytes = content.getBytes();
                // Отправляем полный ответ: Статус + Заголовки + Тело
                sendResponse(out, 200, "OK", mimeType, contentBytes.length, contentBytes);
                return;
            }
            // 5. Для остальных файлов: сначала отправляем заголовки, потом стримим сам файл
            long length = Files.size(filePath);
            // Формируем Status Line: "HTTP/1.1 404 Not Found\r\n"
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