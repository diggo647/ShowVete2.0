package br.com.showvete.api;

import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mini biblioteca de JSON (só o que a API precisa), para o projeto rodar sem dependências extras.
 * Em projetos grandes, use Jackson ou Gson.
 */
public final class Json {

    private Json() { }

    /** Monta um objeto JSON a partir de pares: chave, valor, chave, valor... (mantém a ordem). */
    public static Map<String, Object> objeto(Object... pares) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) {
            mapa.put((String) pares[i], pares[i + 1]);
        }
        return mapa;
    }

    /* ======================= ESCREVER ======================= */

    public static String escrever(Object valor) {
        StringBuilder sb = new StringBuilder();
        escrever(valor, sb);
        return sb.toString();
    }

    private static void escrever(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Double d && (d.isNaN() || d.isInfinite())) {
            sb.append("null");
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> mapa) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                texto(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> itens) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : itens) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escrever(item, sb);
            }
            sb.append(']');
        } else if (v instanceof String || v instanceof Enum<?> || v instanceof TemporalAccessor) {
            texto(v.toString(), sb);
        } else {
            texto(v.toString(), sb);
        }
    }

    private static void texto(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    /* ======================= LER ======================= */

    /** Converte texto JSON em Map, List, String, Double, Boolean ou null. */
    public static Object ler(String texto) {
        Leitor leitor = new Leitor(texto);
        Object valor = leitor.valor(0);
        leitor.espaco();
        if (leitor.pos != texto.length()) throw leitor.erro("há texto sobrando no final");
        return valor;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> lerObjeto(String texto) {
        Object valor = ler(texto);
        if (valor instanceof Map<?, ?>) return (Map<String, Object>) valor;
        throw new IllegalArgumentException("O corpo da requisição deve ser um objeto JSON.");
    }

    private static final class Leitor {
        private final String s;
        private int pos;

        Leitor(String s) { this.s = s; }

        IllegalArgumentException erro(String mensagem) {
            return new IllegalArgumentException("JSON inválido (posição " + pos + "): " + mensagem + ".");
        }

        void espaco() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char espiar() {
            if (pos >= s.length()) throw erro("fim inesperado");
            return s.charAt(pos);
        }

        char proximo() {
            char c = espiar();
            pos++;
            return c;
        }

        Object valor(int nivel) {
            if (nivel > 32) throw erro("estrutura profunda demais");
            espaco();

            return switch (espiar()) {
                case '{' -> objeto(nivel);
                case '[' -> lista(nivel);
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> numero();
            };
        }

        Map<String, Object> objeto(int nivel) {
            Map<String, Object> mapa = new LinkedHashMap<>();
            pos++;                       // pula '{'
            espaco();
            if (espiar() == '}') { pos++; return mapa; }

            while (true) {
                espaco();
                if (espiar() != '"') throw erro("esperava o nome de um campo");
                String chave = string();
                espaco();
                if (proximo() != ':') throw erro("esperava ':' depois do nome do campo");
                mapa.put(chave, valor(nivel + 1));
                espaco();
                char c = proximo();
                if (c == '}') return mapa;
                if (c != ',') throw erro("esperava ',' ou '}'");
            }
        }

        List<Object> lista(int nivel) {
            List<Object> itens = new ArrayList<>();
            pos++;                       // pula '['
            espaco();
            if (espiar() == ']') { pos++; return itens; }

            while (true) {
                itens.add(valor(nivel + 1));
                espaco();
                char c = proximo();
                if (c == ']') return itens;
                if (c != ',') throw erro("esperava ',' ou ']'");
            }
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            pos++;                       // pula a aspa inicial
            while (true) {
                char c = proximo();
                if (c == '"') return sb.toString();
                if (c < 0x20) throw erro("caractere de controle dentro do texto");
                if (c != '\\') { sb.append(c); continue; }

                char e = proximo();
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw erro("sequência \\u incompleta");
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw erro("sequência \\u inválida");
                        }
                        pos += 4;
                    }
                    default -> throw erro("escape inválido");
                }
            }
        }

        Object literal(String palavra, Object valor) {
            if (!s.startsWith(palavra, pos)) throw erro("valor inesperado");
            pos += palavra.length();
            return valor;
        }

        Double numero() {
            int inicio = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            if (inicio == pos) throw erro("valor inesperado '" + s.charAt(pos) + "'");
            try {
                return Double.parseDouble(s.substring(inicio, pos));
            } catch (NumberFormatException e) {
                throw erro("número inválido");
            }
        }
    }
}
