package br.com.showvete.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;

/** Entrega os arquivos da pasta web/ (index.html, style.css, script.js). */
public class ArquivosEstaticos implements HttpHandler {

    private static final Map<String, String> TIPOS = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".js", "text/javascript; charset=utf-8",
            ".json", "application/json; charset=utf-8",
            ".svg", "image/svg+xml",
            ".png", "image/png",
            ".ico", "image/x-icon");

    private final Path base;

    public ArquivosEstaticos(Path base) {
        this.base = base.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            enviar(ex, 405, "text/plain; charset=utf-8", "Método não permitido".getBytes(StandardCharsets.UTF_8));
            return;
        }

        String caminho = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if (caminho.equals("/")) caminho = "/index.html";

        try {
            Path arquivo = base.resolve(caminho.substring(1)).normalize();

            // segurança: nunca sair da pasta web/ (bloqueia "../")
            if (arquivo.startsWith(base) && Files.isRegularFile(arquivo)) {
                String nome = arquivo.getFileName().toString();
                String extensao = nome.contains(".") ? nome.substring(nome.lastIndexOf('.')) : "";
                enviar(ex, 200, TIPOS.getOrDefault(extensao, "application/octet-stream"), Files.readAllBytes(arquivo));
                return;
            }
        } catch (InvalidPathException e) {
            // cai no 404 abaixo
        }

        enviar(ex, 404, "text/plain; charset=utf-8", "Página não encontrada".getBytes(StandardCharsets.UTF_8));
    }

    private static void enviar(HttpExchange ex, int status, String tipo, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream saida = ex.getResponseBody()) {
            saida.write(bytes);
        }
    }
}
