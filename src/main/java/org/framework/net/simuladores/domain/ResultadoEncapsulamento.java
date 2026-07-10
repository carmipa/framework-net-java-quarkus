package org.framework.net.simuladores.domain;

import java.util.List;

/**
 * Resultado do simulador de <b>encapsulamento</b>: a mensagem da aplicação embrulhada
 * camada a camada (modelo TCP/IP mapeado no OSI), do emissor (App → Enlace) até o quadro
 * completo. As camadas vêm na ordem de encapsulamento (nível 7 primeiro).
 */
public record ResultadoEncapsulamento(
        boolean ok,
        String erro,
        String mensagem,        // dados da aplicação (eco da entrada)
        String transporte,      // "TCP" | "UDP"
        int totalBytes,         // tamanho final do quadro Ethernet
        List<Camada> camadas) {

    /** Uma camada do encapsulamento com seu cabeçalho, PDU e tamanhos. */
    public record Camada(
            int nivel,          // 7, 4, 3, 2 (OSI)
            String nome,        // "Aplicação", "Transporte", ...
            String protocolo,   // "HTTP", "TCP", "IPv4", "Ethernet II"
            String pdu,         // "Dados", "Segmento", "Pacote", "Quadro"
            String icone,       // Material Symbol
            int headerBytes,
            int payloadBytes,
            int totalBytes,
            List<Campo> cabecalho,
            String resumoHex) {
    }

    /** Um campo do cabeçalho da camada (nome, valor e explicação didática). */
    public record Campo(String nome, String valor, String descricao) {
    }

    public static ResultadoEncapsulamento erroDe(String mensagem) {
        return new ResultadoEncapsulamento(false, mensagem, "", "", 0, List.of());
    }
}
