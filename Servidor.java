package br.com.showvete.api;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * Sobe um único servidor que entrega o SITE (pasta web/) e a API (/api/...).
 * Variáveis opcionais: PORT (padrão 8080), HOST (padrão 127.0.0.1) e WEB_DIR (padrão "web").
 */
public class Servidor {

    public static void main(String[] args) throws IOException {
        int porta = Integer.parseInt(env("PORT", "8080"));
        String host = env("HOST", "127.0.0.1");
        Path web = Path.of(env("WEB_DIR", "web")).toAbsolutePath().normalize();

        if (!Files.isDirectory(web)) {
            System.err.println("Pasta do site não encontrada: " + web);
            System.err.println("Rode o programa a partir da pasta do projeto ou defina WEB_DIR.");
            System.exit(1);
        }

        HttpServer servidor = HttpServer.create(new InetSocketAddress(InetAddress.getByName(host), porta), 0);
        servidor.createContext("/api/", new ApiHandler());
        servidor.createContext("/", new ArquivosEstaticos(web));
        servidor.setExecutor(Executors.newFixedThreadPool(8));
        servidor.start();

        System.out.println("✦ Show Vete no ar!  Abra: http://" + (host.equals("0.0.0.0") ? "localhost" : host) + ":" + porta);
        System.out.println("  Site: " + web);
        System.out.println("  Teste a conexão com o banco em: /api/saude   (Ctrl+C para parar)");
    }

    private static String env(String chave, String padrao) {
        String valor = System.getenv(chave);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
