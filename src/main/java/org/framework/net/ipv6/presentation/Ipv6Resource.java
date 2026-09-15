package org.framework.net.ipv6.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.framework.net.ipv6.application.Ipv6CidrService;
import org.framework.net.ipv6.exception.Ipv6Exception;

/**
 * Rotas da Calculadora IPv6.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO:</b> expõe a calculadora IPv6 em {@code /ipv6} — análise de
 * endereço/prefixo e divisão de prefixo em sub-redes — preenchendo a lacuna de "não temos
 * calculadora IPv6". Espelha a estrutura da Calculadora IPv4 (HTMX, fragmentos).</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO:</b> todas as rotas do módulo vivem neste único resource (dois
 * {@code @Path} sob {@code /ipv6/api/*} em resources diferentes fariam o JAX-RS devolver 404 —
 * armadilha já enfrentada no Tráfego). Cada POST devolve fragmento HTML para o htmx trocar só o
 * painel de resultado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA:</b> entrada inválida sobe como {@link Ipv6Exception},
 * convertida pelo {@code Ipv6ExceptionMapper} em 400 (fragmento htmx ou texto puro).</p>
 */
@Path("/ipv6")
public class Ipv6Resource {

    @Inject
    Ipv6CidrService service;

    @Inject
    @Location("ipv6/index.html")
    Template index;

    @Inject
    @Location("ipv6/partials/resultado_calc.html")
    Template resultadoCalc;

    @Inject
    @Location("ipv6/partials/resultado_divisao.html")
    Template resultadoDivisao;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance pagina() {
        return index.data("activeMainMenu", "ipv6");
    }

    /** Analisa um endereço ou prefixo IPv6 (normalização, tipo, rede, faixa, IID, binário). */
    @POST
    @Path("/api/calcular")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance calcular(@FormParam("endereco") String endereco) {
        return resultadoCalc.data("r", service.analisar(endereco));
    }

    /** Divide um prefixo base em sub-redes do prefixo alvo. */
    @POST
    @Path("/api/dividir")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance dividir(
            @FormParam("bloco") String bloco,
            @FormParam("prefixoAlvo") String prefixoAlvo) {
        return resultadoDivisao.data("d", service.dividir(bloco, parsePrefixo(prefixoAlvo)));
    }

    private int parsePrefixo(String bruto) {
        String txt = bruto == null ? "" : bruto.strip();
        if (txt.startsWith("/")) {
            txt = txt.substring(1).strip();
        }
        if (txt.isEmpty()) {
            throw new Ipv6Exception("Informe o prefixo alvo (ex.: 64).");
        }
        try {
            return Integer.parseInt(txt);
        } catch (NumberFormatException ex) {
            throw new Ipv6Exception("Prefixo alvo inválido: use um número de 0 a 128.");
        }
    }
}
