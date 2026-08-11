package org.framework.net.paginaErros.presentation;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.framework.net.paginaErros.application.PaginaErroService;
import org.framework.net.paginaErros.application.PaginaErroService.DadosPaginaErro;
import org.framework.net.telemetria.TelemetriaLogger;

/**
 * Traduz qualquer falha não tratada na página de erro do Framework.
 *
 * <p><b>Propósito de negócio:</b> URL digitada errada, sessão expirada ou rota
 * inexistente devolviam a tela padrão do servidor — branca, em inglês e sem
 * caminho de volta. Este mapper entrega, no lugar, a página no mesmo desenho do
 * app, com o que aconteceu, o {@code trace_id} real e atalhos para os módulos.</p>
 *
 * <p><b>Invariantes do domínio:</b> HTML é para gente, JSON é para máquina. A
 * página só é devolvida quando o cliente pede {@code text/html} <b>e</b> a rota
 * não é de API — do contrário devolver HTML quebraria em silêncio todo
 * {@code fetch()} do frontend, que faria {@code response.json()} sobre uma página
 * inteira. Resposta de erro já formatada por outro componente (o 429 do rate
 * limit, por exemplo) é preservada tal como veio.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> se a própria renderização do template
 * falhar, cai para uma resposta de texto simples com o código correto — o mapper
 * jamais pode ser a origem de um segundo erro, que produziria recursão. Detalhe
 * interno da exceção (classe, mensagem, stack) nunca vai para a tela: o que liga
 * o usuário ao diagnóstico é o {@code trace_id}, e vazar interno de exceção em
 * página pública é entregar mapa da aplicação a quem estiver sondando.</p>
 */
@Provider
public class PaginaErroMapper implements ExceptionMapper<Throwable> {

    private static final String TIPO_HTML = MediaType.TEXT_HTML + ";charset=UTF-8";

    @Inject
    PaginaErroService paginaErroService;

    @Inject
    TelemetriaLogger telemetriaLogger;

    @Inject
    @Location("paginaErros/erro.html")
    Template pagina;

    @Context
    UriInfo uriInfo;

    @Context
    HttpHeaders httpHeaders;

    @Context
    Request request;

    @Override
    public Response toResponse(Throwable exception) {
        int status = statusDe(exception);
        String caminho = caminhoAtual();
        String metodo = metodoAtual();

        if (status >= 500) {
            telemetriaLogger.logException("paginaErros", "falha_nao_tratada", null, exception);
        }

        if (!querHtml(caminho)) {
            return respostaTecnica(exception, status);
        }

        try {
            DadosPaginaErro dados = paginaErroService.montar(status, caminho, metodo);
            String html = pagina
                    .data("erro", dados.erro())
                    .data("dados", dados)
                    .render();
            return Response.status(status).entity(html).type(TIPO_HTML).build();
        } catch (RuntimeException falhaNaPagina) {
            // A página de erro não pode gerar erro: cai para texto simples.
            telemetriaLogger.logException("paginaErros", "falha_na_pagina_de_erro", null, falhaNaPagina);
            return Response.status(status)
                    .entity("HTTP " + status + " — não foi possível renderizar a página de erro.")
                    .type(MediaType.TEXT_PLAIN + ";charset=UTF-8")
                    .build();
        }
    }

    /**
     * Status a apresentar.
     *
     * <p><b>Comportamento em caso de falha:</b> exceção sem status próprio vira
     * 500 — falhar para o código mais grave evita apresentar um erro de servidor
     * como se fosse culpa do usuário.</p>
     */
    private int statusDe(Throwable exception) {
        if (exception instanceof WebApplicationException wae && wae.getResponse() != null) {
            return wae.getResponse().getStatus();
        }
        return Response.Status.INTERNAL_SERVER_ERROR.getStatusCode();
    }

    /**
     * O cliente quer uma página?
     *
     * <p><b>Invariantes do domínio:</b> rota de API nunca recebe HTML, mesmo que o
     * cabeçalho {@code Accept} peça — é o caminho por onde o frontend conversa com
     * o backend, e uma página inteira no lugar do JSON quebra o
     * {@code response.json()} sem mensagem de erro compreensível.</p>
     */
    private boolean querHtml(String caminho) {
        if (caminho.contains("/api/") || caminho.endsWith("/api")) {
            return false;
        }
        try {
            return httpHeaders.getAcceptableMediaTypes().stream()
                    .anyMatch(tipo -> tipo.isCompatible(MediaType.TEXT_HTML_TYPE)
                            && !tipo.isWildcardType());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Resposta para cliente que não é navegador.
     *
     * <p><b>Invariantes do domínio:</b> resposta já montada por outro componente
     * (rate limit, filtros de segurança) é devolvida intacta — reescrevê-la
     * apagaria a mensagem que aquele componente escolheu dar.</p>
     */
    private Response respostaTecnica(Throwable exception, int status) {
        if (exception instanceof WebApplicationException wae && wae.getResponse() != null
                && wae.getResponse().hasEntity()) {
            return wae.getResponse();
        }
        return Response.status(status)
                .entity("{\"status\":" + status + ",\"erro\":\""
                        + org.framework.net.paginaErros.domain.CatalogoErros.porCodigo(status).titulo()
                        + "\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    private String caminhoAtual() {
        try {
            String caminho = uriInfo.getPath();
            if (caminho == null || caminho.isBlank()) {
                return "/";
            }
            return caminho.startsWith("/") ? caminho : "/" + caminho;
        } catch (RuntimeException ex) {
            return "/";
        }
    }

    private String metodoAtual() {
        try {
            return request.getMethod();
        } catch (RuntimeException ex) {
            return "GET";
        }
    }
}
