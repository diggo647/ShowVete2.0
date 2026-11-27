package br.com.showvete.api;

import br.com.showvete.dao.AgendamentoDAO;
import br.com.showvete.dao.CampanhaDAO;
import br.com.showvete.dao.Conexao;
import br.com.showvete.dao.FuncionarioDAO;
import br.com.showvete.dao.PedidoDAO;
import br.com.showvete.dao.PetDAO;
import br.com.showvete.dao.ProdutoDAO;
import br.com.showvete.dao.ServicoDAO;
import br.com.showvete.dao.TutorDAO;
import br.com.showvete.model.Cachorro;
import br.com.showvete.model.Carrinho;
import br.com.showvete.model.CampanhaVacinacao;
import br.com.showvete.model.EscalaDia;
import br.com.showvete.model.FormaPagamento;
import br.com.showvete.model.Funcionario;
import br.com.showvete.model.Gato;
import br.com.showvete.model.Pet;
import br.com.showvete.model.Produto;
import br.com.showvete.model.Servico;
import br.com.showvete.model.TipoEntrega;
import br.com.showvete.model.Tutor;
import br.com.showvete.service.AgendamentoService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recebe as requisições do site em /api/... , usa os DAOs/serviços e devolve JSON.
 *
 *   GET  /api/saude        GET  /api/config       GET  /api/servicos
 *   GET  /api/produtos     GET  /api/equipe       GET  /api/campanhas
 *   GET  /api/escala?data=AAAA-MM-DD              GET  /api/horarios?data=AAAA-MM-DD
 *   POST /api/pedidos      POST /api/agendamentos
 */
public class ApiHandler implements HttpHandler {

    private static final int LIMITE_CORPO = 100_000;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final ServicoDAO servicoDAO = new ServicoDAO();
    private final ProdutoDAO produtoDAO = new ProdutoDAO();
    private final FuncionarioDAO funcionarioDAO = new FuncionarioDAO();
    private final CampanhaDAO campanhaDAO = new CampanhaDAO();
    private final TutorDAO tutorDAO = new TutorDAO();
    private final PetDAO petDAO = new PetDAO();
    private final PedidoDAO pedidoDAO = new PedidoDAO();
    private final AgendamentoService agendamentoService = new AgendamentoService();

    /** Erro com código HTTP próprio (404, 405...). */
    private static final class ErroHttp extends RuntimeException {
        final int status;
        ErroHttp(int status, String mensagem) { super(mensagem); this.status = status; }
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        int status = 200;
        Object corpo;

        try {
            String metodo = ex.getRequestMethod();
            String caminho = ex.getRequestURI().getPath();
            Map<String, String> consulta = lerConsulta(ex.getRequestURI().getRawQuery());

            switch (caminho) {
                case "/api/saude"       -> { exigir(metodo, "GET");  corpo = saude(); }
                case "/api/config"      -> { exigir(metodo, "GET");  corpo = config(); }
                case "/api/servicos"    -> { exigir(metodo, "GET");  corpo = listarServicos(); }
                case "/api/produtos"    -> { exigir(metodo, "GET");  corpo = listarProdutos(); }
                case "/api/equipe"      -> { exigir(metodo, "GET");  corpo = listarEquipe(); }
                case "/api/campanhas"   -> { exigir(metodo, "GET");  corpo = listarCampanhas(); }
                case "/api/escala"      -> { exigir(metodo, "GET");  corpo = escala(consulta); }
                case "/api/horarios"    -> { exigir(metodo, "GET");  corpo = horarios(consulta); }
                case "/api/pedidos"     -> { exigir(metodo, "POST"); corpo = criarPedido(lerCorpo(ex)); status = 201; }
                case "/api/agendamentos"-> { exigir(metodo, "POST"); corpo = criarAgendamento(lerCorpo(ex)); status = 201; }
                default -> throw new ErroHttp(404, "Rota não encontrada: " + caminho);
            }

        } catch (ErroHttp e) {
            status = e.status;
            corpo = Json.objeto("erro", e.getMessage());
        } catch (IllegalArgumentException e) {
            status = 400;     // dado inválido enviado pelo site
            corpo = Json.objeto("erro", e.getMessage());
        } catch (SQLException e) {
            System.err.println("Erro de banco: " + e.getMessage());
            String estado = e.getSQLState();

            if ("23505".equals(estado)) {
                status = 409;
                corpo = Json.objeto("erro", "Esse registro já existe.");
            } else if (estado == null || estado.startsWith("08")) {
                status = 503;
                corpo = Json.objeto("erro", "Não foi possível acessar o banco de dados. Confira se o PostgreSQL está ligado.");
            } else {
                status = 500;
                corpo = Json.objeto("erro", "Erro ao acessar o banco de dados.");
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            status = 500;
            corpo = Json.objeto("erro", "Erro interno do servidor.");
        }

        responder(ex, status, corpo);
    }

    /* ============================ GET ============================ */

    private Object saude() {
        Map<String, Object> resposta = Json.objeto("api", "ok");
        try (Connection con = Conexao.obter()) {
            resposta.put("banco", "ok");
        } catch (SQLException e) {
            resposta.put("banco", "indisponivel");
        }
        return resposta;
    }

    private Object config() {
        return Json.objeto(
                "taxaEntrega", Carrinho.TAXA_ENTREGA,
                "maxPorHorario", AgendamentoService.MAX_POR_HORARIO,
                "primeiroHorario", AgendamentoService.PRIMEIRO_HORARIO,
                "ultimoHorario", AgendamentoService.ULTIMO_HORARIO);
    }

    private Object listarServicos() throws SQLException {
        List<Object> lista = new ArrayList<>();
        for (Servico s : servicoDAO.listar()) lista.add(Mapeador.servico(s));
        return lista;
    }

    private Object listarProdutos() throws SQLException {
        List<Object> lista = new ArrayList<>();
        for (Produto p : produtoDAO.listar()) lista.add(Mapeador.produto(p));
        return lista;
    }

    private Object listarEquipe() throws SQLException {
        List<Object> lista = new ArrayList<>();
        for (Funcionario f : funcionarioDAO.listar()) lista.add(Mapeador.funcionario(f));
        return lista;
    }

    private Object listarCampanhas() throws SQLException {
        List<Object> lista = new ArrayList<>();
        for (CampanhaVacinacao c : campanhaDAO.listar()) lista.add(Mapeador.campanha(c));
        return lista;
    }

    private Object escala(Map<String, String> consulta) throws SQLException {
        LocalDate data = lerData(consulta.get("data"));

        if (data.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return Json.objeto("data", data, "aberto", false,
                    "nomeDoDia", DayOfWeek.SUNDAY.getDisplayName(TextStyle.FULL, PT_BR));
        }

        Optional<EscalaDia> escala = funcionarioDAO.escalaDoDia(data.getDayOfWeek().getValue());
        if (escala.isPresent()) {
            Map<String, Object> resposta = Json.objeto("data", data);
            resposta.putAll(Mapeador.escala(escala.get()));
            return resposta;
        }
        return Json.objeto("data", data, "aberto", true, "animais", List.of(), "caixa", null);
    }

    private Object horarios(Map<String, String> consulta) throws SQLException {
        LocalDate data = lerData(consulta.get("data"));
        List<LocalTime> livres = agendamentoService.horariosLivres(data);

        return Json.objeto("data", data,
                "aberto", data.getDayOfWeek() != DayOfWeek.SUNDAY,
                "horarios", livres);
    }

    /* ============================ POST: pedido ============================ */

    private Object criarPedido(String texto) throws SQLException {
        Map<String, Object> c = Json.lerObjeto(texto);

        String nome = texto(c, "nome", true, 100);
        String telefone = texto(c, "telefone", false, 20);
        String email = email(c);

        TipoEntrega entrega = c.get("entrega") == null
                ? TipoEntrega.RETIRADA
                : enumDe(TipoEntrega.class, texto(c, "entrega", true, 10), "entrega");
        String endereco = entrega == TipoEntrega.ENTREGA ? texto(c, "endereco", true, 200) : null;

        if (!(c.get("itens") instanceof List<?> itens) || itens.isEmpty()) {
            throw new IllegalArgumentException("O pedido precisa ter pelo menos um item.");
        }
        if (itens.size() > 50) {
            throw new IllegalArgumentException("Itens demais no pedido (máximo 50).");
        }

        // Monta o carrinho com os PREÇOS DO BANCO (nunca confiamos no preço que veio do navegador)
        Carrinho carrinho = new Carrinho();
        carrinho.setEntrega(entrega);

        for (Object bruto : itens) {
            if (!(bruto instanceof Map<?, ?> mapa)) {
                throw new IllegalArgumentException("Item de pedido inválido.");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) mapa;

            int produtoId = inteiro(item, "produtoId");
            int quantidade = inteiro(item, "quantidade");
            if (quantidade < 1 || quantidade > 20) {
                throw new IllegalArgumentException("A quantidade de cada item deve ser de 1 a 20.");
            }

            Produto produto = produtoDAO.buscarPorId(produtoId)
                    .orElseThrow(() -> new IllegalArgumentException("Produto " + produtoId + " não existe."));

            for (int i = 0; i < quantidade; i++) carrinho.adicionar(produto);
        }

        Tutor tutor = tutorDAO.buscarOuCriar(nome, telefone, email);
        int numero = pedidoDAO.salvar(carrinho, tutor.getId(), endereco);

        return Json.objeto(
                "pedido", numero,
                "itens", carrinho.quantidadeTotal(),
                "subtotal", carrinho.subtotal(),
                "taxaEntrega", carrinho.taxaEntrega(),
                "total", carrinho.total());
    }

    /* ============================ POST: agendamento ============================ */

    private Object criarAgendamento(String texto) throws SQLException {
        Map<String, Object> c = Json.lerObjeto(texto);

        String tutorNome = texto(c, "tutorNome", true, 100);
        String telefone = texto(c, "telefone", false, 20);
        String email = email(c);
        String petNome = texto(c, "petNome", true, 80);
        String tipo = texto(c, "tipo", true, 10).toUpperCase();
        int idade = inteiro(c, "idade");
        int servicoId = inteiro(c, "servicoId");
        LocalDate data = lerData(texto(c, "data", true, 10));
        LocalTime hora = lerHora(texto(c, "horario", true, 5));
        FormaPagamento pagamento = enumDe(FormaPagamento.class, texto(c, "pagamento", true, 10), "pagamento");
        String observacoes = texto(c, "observacoes", false, 300);

        // 1) valida tudo o que NÃO depende do banco
        montarPet(tipo, c, petNome, idade, 0);

        // 2) valida o que depende do banco (serviço existe? horário livre?) antes de cadastrar qualquer coisa
        Servico servico = null;
        for (Servico s : servicoDAO.listar()) {
            if (s.id() == servicoId) servico = s;
        }
        if (servico == null) throw new IllegalArgumentException("Serviço não encontrado.");

        agendamentoService.verificarDisponibilidade(data, hora);

        // 3) cadastra (ou reaproveita) tutor e pet
        Tutor tutor = tutorDAO.buscarOuCriar(tutorNome, telefone, email);

        Optional<Pet> existente = petDAO.buscarPorTutorENome(tutor.getId(), petNome);
        Pet pet = existente.isPresent()
                ? existente.get()
                : petDAO.inserir(montarPet(tipo, c, petNome, idade, tutor.getId()));

        // 4) agenda
        int numero = agendamentoService.agendar(pet.getId(), servico.id(), data, hora, pagamento, observacoes);

        Optional<EscalaDia> escala = funcionarioDAO.escalaDoDia(data.getDayOfWeek().getValue());

        return Json.objeto(
                "agendamento", numero,
                "tutor", tutor.getNome(),
                "pet", pet.getNome(),
                "servico", servico.nome(),
                "data", data,
                "horario", hora,
                "pagamento", pagamento.getRotulo(),
                "equipe", escala.isPresent() ? Mapeador.escala(escala.get()) : null);
    }

    /** Cria Cachorro ou Gato conforme o tipo (as próprias classes validam os dados). */
    private Pet montarPet(String tipo, Map<String, Object> c, String nome, int idade, int tutorId) {
        return switch (tipo) {
            case "CACHORRO" -> new Cachorro(null, nome, idade, tutorId,
                    enumDe(Cachorro.Porte.class, texto(c, "porte", true, 10), "porte"));
            case "GATO" -> new Gato(null, nome, idade, tutorId,
                    enumDe(Gato.Pelagem.class, texto(c, "pelagem", true, 10), "pelagem"));
            default -> throw new IllegalArgumentException("Tipo de pet inválido. Use CACHORRO ou GATO.");
        };
    }

    /* ============================ utilitários ============================ */

    private static void exigir(String metodo, String esperado) {
        if (!esperado.equals(metodo)) {
            throw new ErroHttp(405, "Método não permitido. Use " + esperado + ".");
        }
    }

    private static String lerCorpo(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(LIMITE_CORPO + 1);
        if (bytes.length > LIMITE_CORPO) throw new ErroHttp(413, "Requisição grande demais.");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Map<String, String> lerConsulta(String bruta) {
        Map<String, String> mapa = new LinkedHashMap<>();
        if (bruta == null || bruta.isBlank()) return mapa;

        for (String par : bruta.split("&")) {
            int i = par.indexOf('=');
            String chave = URLDecoder.decode(i < 0 ? par : par.substring(0, i), StandardCharsets.UTF_8);
            String valor = i < 0 ? "" : URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8);
            mapa.put(chave, valor);
        }
        return mapa;
    }

    private static LocalDate lerData(String valor) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("Informe a data (AAAA-MM-DD).");
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Data inválida. Use o formato AAAA-MM-DD.");
        }
    }

    private static LocalTime lerHora(String valor) {
        try {
            return LocalTime.parse(valor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Horário inválido. Use o formato HH:mm.");
        }
    }

    private static String texto(Map<String, Object> c, String campo, boolean obrigatorio, int maximo) {
        Object valor = c.get(campo);

        if (valor == null || (valor instanceof String vazio && vazio.isBlank())) {
            if (obrigatorio) throw new IllegalArgumentException("O campo '" + campo + "' é obrigatório.");
            return null;
        }
        if (!(valor instanceof String bruto)) {
            throw new IllegalArgumentException("O campo '" + campo + "' deve ser um texto.");
        }

        String limpo = bruto.trim();
        if (limpo.length() > maximo) {
            throw new IllegalArgumentException("O campo '" + campo + "' aceita no máximo " + maximo + " caracteres.");
        }
        return limpo;
    }

    private static String email(Map<String, Object> c) {
        String email = texto(c, "email", true, 120);
        if (!EMAIL.matcher(email).matches()) throw new IllegalArgumentException("E-mail inválido.");
        return email;
    }

    private static int inteiro(Map<String, Object> c, String campo) {
        if (c.get(campo) instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1_000_000) {
            return d.intValue();
        }
        throw new IllegalArgumentException("O campo '" + campo + "' deve ser um número inteiro.");
    }

    private static <E extends Enum<E>> E enumDe(Class<E> tipo, String valor, String campo) {
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Valor inválido para '" + campo + "'. Use um destes: "
                    + Arrays.toString(tipo.getEnumConstants()) + ".");
        }
    }

    private static void responder(HttpExchange ex, int status, Object corpo) throws IOException {
        byte[] bytes = Json.escrever(corpo).getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);

        try (OutputStream saida = ex.getResponseBody()) {
            saida.write(bytes);
        }
        System.out.println(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " -> " + status);
    }
}
