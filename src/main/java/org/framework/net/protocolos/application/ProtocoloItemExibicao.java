package org.framework.net.protocolos.application;

import org.framework.net.protocolos.domain.ProtocoloItem;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record ProtocoloItemExibicao(
        String nome,
        String camada,
        String transporte,
        String portaComum,
        String funcao,
        String seguro,
        String badge,
        String badgeColor,
        String alcance,
        String alcanceLower,
        String categoria,
        String algoritmo,
        String metrica,
        String distanciaAdministrativa,
        String atualizacao,
        String sintaxeBase,
        String dicaDidatica,
        String convergencia,
        String ecmp,
        String problemasComuns,
        String mitigacoes,
        String casoUsoReal,
        String diagnosticoComandos,
        String searchText,
        boolean roteamento) {

    public static ProtocoloItemExibicao from(ProtocoloItem item) {
        String alcance = normalizarOu(item.alcance(), "N/A");
        String categoria = normalizarOu(item.categoria(), "outros");
        String algoritmo = normalizar(item.algoritmo());
        String metrica = normalizar(item.metrica());
        String ad = normalizar(item.distanciaAdministrativa());
        String sintaxe = normalizar(item.sintaxeBase());
        String dica = normalizar(item.dicaDidatica());
        String problemas = normalizar(item.problemasComuns());
        String mitigacoesVal = normalizar(item.mitigacoes());
        String diagnostico = normalizarOu(item.diagnosticoComandos(), "show ip route");

        String searchText = Stream.of(
                        item.nome(),
                        item.camada(),
                        alcance,
                        item.transporte(),
                        item.portaComum(),
                        item.seguro(),
                        item.badge(),
                        item.funcao(),
                        algoritmo,
                        metrica,
                        ad,
                        sintaxe,
                        dica,
                        problemas,
                        mitigacoesVal,
                        diagnostico)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));

        return new ProtocoloItemExibicao(
                item.nome(),
                item.camada(),
                item.transporte(),
                item.portaComum(),
                item.funcao(),
                item.seguro(),
                item.badge(),
                item.badgeColor(),
                alcance,
                alcance.toLowerCase(Locale.ROOT),
                categoria,
                algoritmo,
                metrica,
                ad,
                normalizar(item.atualizacao()),
                sintaxe,
                dica,
                normalizar(item.convergencia()),
                normalizar(item.ecmp()),
                problemas,
                mitigacoesVal,
                normalizar(item.casoUsoReal()),
                diagnostico,
                searchText,
                "roteamento".equalsIgnoreCase(categoria));
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private static String normalizarOu(String valor, String fallback) {
        String normalizado = normalizar(valor);
        return normalizado.isEmpty() ? fallback : normalizado;
    }
}
