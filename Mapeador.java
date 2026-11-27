package br.com.showvete.api;

import br.com.showvete.model.CampanhaVacinacao;
import br.com.showvete.model.EscalaDia;
import br.com.showvete.model.Funcionario;
import br.com.showvete.model.Produto;
import br.com.showvete.model.Racao;
import br.com.showvete.model.Servico;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Decide quais campos de cada objeto aparecem na resposta da API. */
public final class Mapeador {

    private Mapeador() { }

    public static Map<String, Object> servico(Servico s) {
        return Json.objeto("id", s.id(), "nome", s.nome(), "preco", s.preco(), "aPartirDe", s.aPartirDe());
    }

    public static Map<String, Object> produto(Produto p) {
        return Json.objeto(
                "id", p.getId(),
                "nome", p.getNome(),
                "descricao", p.getDescricao(),
                "preco", p.getPreco(),
                "categoria", p instanceof Racao ? "RACAO" : "ACESSORIO",
                "secao", p.getSecao(),
                "icone", p.getIcone());
    }

    public static Map<String, Object> funcionario(Funcionario f) {
        return Json.objeto("id", f.getId(), "nome", f.getNome(), "cargo", f.getCargo());
    }

    public static Map<String, Object> campanha(CampanhaVacinacao c) {
        return Json.objeto("dia", c.dia(), "titulo", c.titulo(), "descricao", c.descricao());
    }

    public static Map<String, Object> escala(EscalaDia e) {
        List<String> animais = new ArrayList<>();
        for (Funcionario f : e.animais()) animais.add(f.getNome());

        return Json.objeto(
                "aberto", true,
                "diaSemana", e.diaSemana(),
                "nomeDoDia", e.nomeDoDia(),
                "animais", animais,
                "caixa", e.caixa() == null ? null : e.caixa().getNome());
    }
}
