package org.framework.net.analiseTrafego;

import org.framework.net.analiseTrafego.application.TrafegoDecoderService;
import org.framework.net.analiseTrafego.domain.model.ResultadoDecodificacao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafegoDecoderServiceTest {

    // Ethernet + IPv4 + TCP (SYN → porta 80), 54 bytes.
    private static final String FRAME_SYN =
            "aabbccddeeff1122334455660800"
                    + "450000281c4640004006b1e6c0a80001c0a80002"
                    + "d4310050000000000000000050027210e5770000";

    // IPv4 CRU (sem cabeçalho Ethernet), 20 bytes, cujo IP de ORIGEM começa com 8.0.x.x —
    // portanto os bytes 12-13 valem 0x0800, o mesmo EtherType de IPv4. Antes do desempate por
    // versão, o modo auto lia isto como "Ethernet II" (achado 4). Cabeçalho autoconsistente:
    // versão 4, IHL 5, Total Length 0x0014 = 20.
    private static final String IPV4_CRU_ORIGEM_0800 =
            "4500001400000000400100000800000101010101";

    private final TrafegoDecoderService service = new TrafegoDecoderService();

    @Test
    void decodificaEthernetIpv4Tcp() {
        ResultadoDecodificacao r = service.decodificar(FRAME_SYN, "auto");
        assertTrue(r.ok());
        assertEquals(54, r.totalBytes());
        assertEquals("ethernet", r.camadaInicial());
        List<String> nomes = r.camadas().stream().map(ResultadoDecodificacao.Camada::nome).toList();
        assertTrue(nomes.contains("Ethernet II"));
        assertTrue(nomes.contains("IPv4"));
        assertTrue(nomes.contains("TCP"));
    }

    @Test
    void ipv4TrazEnderecosCorretos() {
        ResultadoDecodificacao r = service.decodificar(FRAME_SYN, "auto");
        ResultadoDecodificacao.Camada ipv4 = r.camadas().stream()
                .filter(c -> c.nome().equals("IPv4")).findFirst().orElseThrow();
        boolean origem = ipv4.campos().stream()
                .anyMatch(c -> c.nome().equals("IP origem") && c.valor().equals("192.168.0.1"));
        boolean destino = ipv4.campos().stream()
                .anyMatch(c -> c.nome().equals("IP destino") && c.valor().equals("192.168.0.2"));
        assertTrue(origem, "IP origem deve ser 192.168.0.1");
        assertTrue(destino, "IP destino deve ser 192.168.0.2");
    }

    @Test
    void tcpReconheceSynEPortaHttp() {
        ResultadoDecodificacao r = service.decodificar(FRAME_SYN, "auto");
        ResultadoDecodificacao.Camada tcp = r.camadas().stream()
                .filter(c -> c.nome().equals("TCP")).findFirst().orElseThrow();
        boolean syn = tcp.campos().stream().anyMatch(c -> c.valor().contains("SYN"));
        boolean http = tcp.campos().stream().anyMatch(c -> c.valor().contains("HTTP"));
        assertTrue(syn, "flag SYN deve ser detectada");
        assertTrue(http, "porta 80 deve ser rotulada como HTTP");
    }

    @Test
    void rejeitaHexInvalido() {
        assertFalse(service.decodificar("xyz não é hex", "auto").ok());
        assertFalse(service.decodificar("", "auto").ok());
        assertFalse(service.decodificar("abc", "auto").ok()); // ímpar
    }

    @Test
    void autoNaoConfundeIpv4CruComEthernet() {
        ResultadoDecodificacao r = service.decodificar(IPV4_CRU_ORIGEM_0800, "auto");
        assertTrue(r.ok());
        assertEquals("ipv4", r.camadaInicial(),
                "IPv4 cru com IP de origem 8.0.x.x deve ser detectado como ipv4, não ethernet");
        assertEquals("IPv4", r.camadas().get(0).nome());
        // Caso-controle A1: um quadro Ethernet REAL cujo EtherType é 0x0800 continua Ethernet —
        // o desempate não passou a ler todo pacote como IP.
        ResultadoDecodificacao eth = service.decodificar(FRAME_SYN, "auto");
        assertEquals("ethernet", eth.camadaInicial());
        assertEquals("Ethernet II", eth.camadas().get(0).nome());
    }

    /**
     * F42: quadro Ethernet mínimo (60 bytes) = 54 de cabeçalhos + 6 de padding. O Total Length do
     * IPv4 (0x0028 = 40) delimita o datagrama; o que sobra é trailer/padding de enlace, não dado da
     * aplicação (RFC 791 §3.1; é assim que o Wireshark mostra "Padding"). Fronteira (A1): o mesmo
     * TCP com 6 bytes DENTRO do Total Length (0x002e = 46) é payload de verdade.
     */
    @Test
    void paddingEthernetNaoViraPayload() {
        String comPadding = "aabbccddeeff1122334455660800"
                + "450000281c46400040060000c0a80001c0a80002"
                + "c350005000000001000000015010ffff00000000"
                + "000000000000";
        List<String> nomes = service.decodificar(comPadding, "auto").camadas().stream()
                .map(ResultadoDecodificacao.Camada::nome).toList();
        assertFalse(nomes.contains("Payload / dados"), nomes.toString());
        assertEquals("Trailer / padding de enlace", nomes.get(nomes.size() - 1));

        String comDados = "aabbccddeeff1122334455660800"
                + "4500002e1c46400040060000c0a80001c0a80002"
                + "c350005000000001000000015018ffff00000000"
                + "68656c6c6f21";
        List<String> nomesDados = service.decodificar(comDados, "auto").camadas().stream()
                .map(ResultadoDecodificacao.Camada::nome).toList();
        assertEquals("Payload / dados", nomesDados.get(nomesDados.size() - 1));
    }

    /** RFC 5952: o IPv6 do cabeçalho é mostrado comprimido ("2001:db8::1", não "2001:db8:0:0:0:0:0:1"). */
    @Test
    void ipv6DoCabecalhoSaiComprimido() {
        String ipv6Udp = "6000000000081140"
                + "20010db8000000000000000000000001"
                + "20010db8000000000000000000000002"
                + "0035003500080000";
        ResultadoDecodificacao r = service.decodificar(ipv6Udp, "ipv6");
        assertTrue(r.camadas().get(0).resumo().contains("2001:db8::1"), r.camadas().get(0).resumo());
    }

    @Test
    void aceitaSeparadoresComuns() {
        assertEquals("450000281c46", TrafegoDecoderService.normalizar("45 00 00 28 1c:46"));
    }
}
