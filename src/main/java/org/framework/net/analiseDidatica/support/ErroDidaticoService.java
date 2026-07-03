package org.framework.net.analiseDidatica.support;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class ErroDidaticoService {

    public Map<String, String> explicar(String erro) {
        String txt = erro == null ? "" : erro.strip();
        if (txt.isEmpty()) {
            return null;
        }
        String lower = txt.toLowerCase();
        Object[][] rules = {
                {"ip inválido", "O campo IP deve estar em IPv4 com 4 octetos numéricos.", "Use formato x.x.x.x (ex.: 172.19.0.10)."},
                {"cidr", "O prefixo precisa ser inteiro entre 0 e 32.", "Exemplos válidos: 8, 16, 20, 24, 30."},
                {"máscara decimal inválida", "A máscara precisa ter bits contíguos de rede.", "Use máscara contínua, como 255.255.255.0."},
                {"wildcard inválida", "A wildcard deve ser o inverso de uma máscara contígua.", "Ex.: 0.0.15.255 corresponde a /20."},
                {"domínio", "O domínio/hostname não pôde ser resolvido no DNS.", "Teste com google.com e confira conectividade DNS."},
        };
        for (Object[] rule : rules) {
            if (lower.contains(((String) rule[0]).toLowerCase())) {
                Map<String, String> out = new LinkedHashMap<>();
                out.put("causa", (String) rule[1]);
                out.put("como_corrigir", (String) rule[2]);
                return out;
            }
        }
        Map<String, String> out = new LinkedHashMap<>();
        out.put("causa", "A entrada não passou nas validações do modo selecionado.");
        out.put("como_corrigir", "Revise os campos obrigatórios do modo e tente novamente.");
        return out;
    }

    public String motivoAnalise(String modo) {
        return switch (modo == null ? "" : modo) {
            case "cidr" -> "Usuário pediu cálculo de sub-rede CIDR para validar rede/hosts.";
            case "mask" -> "Usuário pediu decomposição didática de máscara decimal e barra.";
            case "wildcard" -> "Usuário pediu análise wildcard (ACL, EIGRP, OSPF, redes Cisco).";
            case "autoip" -> "Usuário pediu descoberta automática de CIDR a partir do IP.";
            case "dominio" -> "Usuário pediu resolução DNS e decomposição técnica do destino.";
            case "ipv6" -> "Usuário pediu análise didática de endereço IPv6.";
            case "comparador" -> "Usuário pediu comparação lado a lado entre dois prefixos CIDR.";
            case "geo" -> "Usuário consultou região geográfica (GeoIP).";
            default -> "Usuário executou análise técnica no framework.";
        };
    }
}
