package org.framework.net.telemetria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Atribuição de módulo no dashboard de telemetria.
 *
 * <p><b>Propósito de negócio:</b> o painel "por módulo" é o que o Paulo olha
 * para saber onde o sistema é usado e onde falha. Uma rota atribuída ao módulo
 * errado não quebra nada — só mente. Este teste trava a tabela de atribuição.</p>
 *
 * <p><b>Invariantes do domínio:</b> toda rota real do projeto tem módulo
 * explícito, e rota desconhecida cai em "Outros" — nunca em um módulo concreto,
 * porque isso inflaria as estatísticas de quem não fez a requisição.</p>
 */
@DisplayName("Telemetria: atribuição de módulo por caminho HTTP")
class ModuloDePathTest {

    @Test
    void rotasDeMenuTemModuloProprio() {
        assertEquals("Início", TelemetriaDashboardService.moduloDePath("/"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/analise"));
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora"));
        assertEquals("Portas", TelemetriaDashboardService.moduloDePath("/portas"));
        assertEquals("Protocolos", TelemetriaDashboardService.moduloDePath("/protocolos"));
        assertEquals("Resolução", TelemetriaDashboardService.moduloDePath("/resolucao-problemas"));
        assertEquals("Localização", TelemetriaDashboardService.moduloDePath("/localizacao"));
        assertEquals("Tráfego", TelemetriaDashboardService.moduloDePath("/trafego"));
        assertEquals("Diagnóstico", TelemetriaDashboardService.moduloDePath("/diagnostico"));
        assertEquals("Segurança ACL", TelemetriaDashboardService.moduloDePath("/seguranca"));
        assertEquals("Telemetria", TelemetriaDashboardService.moduloDePath("/telemetria"));
        assertEquals("Documentação", TelemetriaDashboardService.moduloDePath("/documentacao"));
        assertEquals("Sobre", TelemetriaDashboardService.moduloDePath("/sobre"));
    }

    @Test
    void subcaminhosSeguemOModuloDaRaiz() {
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora/api/dividir"));
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora/api/vlan"));
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora/export/divisao.csv"));
        assertEquals("Segurança ACL", TelemetriaDashboardService.moduloDePath("/seguranca/api/testar"));
        assertEquals("Diagnóstico", TelemetriaDashboardService.moduloDePath("/diagnostico/api/ping"));
        assertEquals("Tráfego", TelemetriaDashboardService.moduloDePath("/trafego/api/decodificar"));
        assertEquals("Localização", TelemetriaDashboardService.moduloDePath("/localizacao/api/cep"));
        // Aprofundamentos por protocolo: são páginas do módulo Protocolos, não
        // módulos próprios — creditá-las a "bgp"/"ssh" partiria as estatísticas
        // do módulo em pedaços que ninguém somaria de volta.
        assertEquals("Protocolos", TelemetriaDashboardService.moduloDePath("/protocolos/bgp"));
        assertEquals("Protocolos", TelemetriaDashboardService.moduloDePath("/protocolos/ssh"));
    }

    @Test
    void resourcesFilhosDaAnaliseContamComoAnalise() {
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/export/json"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/export/pdf"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/history"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/history/catalog"));
        assertEquals("Análise Didática", TelemetriaDashboardService.moduloDePath("/mascara-referencia"));
    }

    @Test
    @DisplayName("regressão: rota desconhecida não pode ser creditada a um módulo real")
    void rotaDesconhecidaCaiEmOutros() {
        assertEquals("Outros", TelemetriaDashboardService.moduloDePath("/rota-que-nao-existe"));
        assertEquals("Outros", TelemetriaDashboardService.moduloDePath("/favicon-inexistente"));
    }

    @Test
    @DisplayName("regressão: /calculadora, /sobre, /admin e /simuladores não são mais Análise Didática")
    void rotasQueEramMalAtribuidas() {
        assertEquals("Calculadora", TelemetriaDashboardService.moduloDePath("/calculadora"));
        assertEquals("Sobre", TelemetriaDashboardService.moduloDePath("/sobre"));
        assertEquals("Admin", TelemetriaDashboardService.moduloDePath("/admin"));
        assertEquals("Admin", TelemetriaDashboardService.moduloDePath("/login"));
        assertEquals("Simuladores", TelemetriaDashboardService.moduloDePath("/simuladores/api/encapsular"));
    }

    @Test
    void apisNaRaizSaoResolvidasPeloSegundoSegmento() {
        assertEquals("GeoIP", TelemetriaDashboardService.moduloDePath("/api/informacoes/geo"));
    }

    @Test
    void caminhoNuloOuVazioNaoQuebra() {
        assertEquals("Início", TelemetriaDashboardService.moduloDePath(null));
        assertEquals("Início", TelemetriaDashboardService.moduloDePath(""));
        assertEquals("Início", TelemetriaDashboardService.moduloDePath("   "));
    }
}
