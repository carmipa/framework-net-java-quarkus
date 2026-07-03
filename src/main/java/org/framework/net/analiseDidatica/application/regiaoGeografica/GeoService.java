package org.framework.net.analiseDidatica.application.regiaoGeografica;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.analiseDidatica.exception.HistoricoPersistenciaException;
import org.framework.net.analiseDidatica.infrastructure.geo.GeoLookupService;
import org.framework.net.analiseDidatica.infrastructure.historico.HistoricoStore;
import org.jboss.logging.Logger;

import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class GeoService {

    private static final Logger LOG = Logger.getLogger(GeoService.class);

    @Inject
    GeoLookupService geoLookupService;

    @Inject
    HistoricoStore historicoStore;

    public String clienteIpEfetivo(String xForwardedFor, String xRealIp, String remoteAddr) {
        java.util.List<String> candidatosRaw = new java.util.ArrayList<>();
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            for (String part : xForwardedFor.split(",")) {
                if (!part.isBlank()) {
                    candidatosRaw.add(part.strip());
                }
            }
        }
        if (xRealIp != null && !xRealIp.isBlank()) {
            candidatosRaw.add(xRealIp.strip());
        }
        if (remoteAddr != null && !remoteAddr.isBlank()) {
            candidatosRaw.add(remoteAddr.strip());
        }

        java.util.List<String> candidatos = candidatosRaw.stream()
                .map(this::normalizarIpTexto)
                .filter(s -> !s.isBlank())
                .toList();
        if (candidatos.isEmpty()) {
            return "";
        }

        for (String ip : candidatos) {
            try {
                InetAddress addr = InetAddress.getByName(ip);
                if (!addr.isSiteLocalAddress() && !addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                    return ip;
                }
            } catch (Exception ignored) {
            }
        }
        return candidatos.get(0);
    }

    public Map<String, Object> executarApiInformacoesGeo(String clienteIp, String rawDigitado) {
        if (rawDigitado != null && !rawDigitado.isBlank()) {
            Optional<String> norm = geoLookupService.normalizarIpDigitado(rawDigitado);
            if (norm.isEmpty()) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("cliente_ip", clienteIp);
                payload.put("consultado", rawDigitado);
                payload.put("modo", "manual");
                payload.put("ok", false);
                payload.put("motivo", "invalid");
                payload.put("mensagem", geoLookupService.mensagemIpInvalido());
                return payload;
            }
            Map<String, Object> geo = geoLookupService.lookupRegiaoGeografica(norm.get());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("cliente_ip", clienteIp);
            payload.put("consultado", norm.get());
            payload.put("modo", "manual");
            payload.putAll(geo);
            registrarHistoricoGeo(payload);
            return payload;
        }

        Map<String, Object> geo = geoLookupService.lookupRegiaoGeografica(clienteIp);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cliente_ip", clienteIp);
        payload.put("consultado", clienteIp);
        payload.put("modo", "ligacao");
        payload.putAll(geo);
        registrarHistoricoGeo(payload);
        return payload;
    }

    public Map<String, Object> montarPaginaInformacoes(String clienteIp, String rawDigitado) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("cliente_ip", clienteIp);
        model.put("ip_digitado_prefill", rawDigitado == null ? "" : rawDigitado);

        if (rawDigitado != null && !rawDigitado.isBlank()) {
            Optional<String> norm = geoLookupService.normalizarIpDigitado(rawDigitado);
            if (norm.isEmpty()) {
                Map<String, Object> geo = geoLookupService.enriquecerRespostaGeo(Map.of(
                        "ok", false,
                        "motivo", "invalid",
                        "mensagem", geoLookupService.mensagemIpInvalido(),
                        "ip", rawDigitado
                ));
                model.put("consultado", rawDigitado);
                model.put("modo_geo", "manual");
                model.put("geo", geo);
                return model;
            }
            model.put("consultado", norm.get());
            model.put("modo_geo", "manual");
            model.put("geo", geoLookupService.lookupRegiaoGeografica(norm.get()));
            return model;
        }

        model.put("consultado", clienteIp);
        model.put("modo_geo", "ligacao");
        model.put("geo", geoLookupService.lookupRegiaoGeografica(clienteIp));
        return model;
    }

    private void registrarHistoricoGeo(Map<String, Object> payloadGeo) {
        String consultado = String.valueOf(payloadGeo.getOrDefault("consultado", "")).strip();
        if (consultado.isEmpty()) {
            return;
        }
        boolean okGeo = Boolean.TRUE.equals(payloadGeo.get("ok"));
        String motivo = String.valueOf(payloadGeo.getOrDefault("motivo", ""));
        String pais;
        String regiao;
        String codigoPais;
        String nivel;
        if (!okGeo && "private_or_local".equals(motivo)) {
            pais = "Local";
            regiao = "Privado";
            codigoPais = "LOCAL";
            nivel = "GeoIP: " + regiao + "/" + pais;
        } else if (okGeo) {
            pais = String.valueOf(payloadGeo.getOrDefault("pais", "N/A"));
            regiao = String.valueOf(payloadGeo.getOrDefault("regiao", "N/A"));
            codigoPais = String.valueOf(payloadGeo.getOrDefault("codigo_pais", ""));
            nivel = "GeoIP: " + regiao + "/" + pais;
        } else {
            pais = "N/A";
            regiao = "Erro";
            codigoPais = "";
            nivel = "GeoIP indisponível (" + (motivo.isBlank() ? "sem detalhe" : motivo) + ")";
        }
        try {
            Map<String, String> entrada = Map.of(
                    "modo", "geo",
                    "ip", consultado,
                    "ipv6", "",
                    "cidr", "",
                    "mask_decimal", codigoPais,
                    "wildcard_mask", ""
            );
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("rede", regiao);
            res.put("broad", pais);
            res.put("mask", "N/A");
            res.put("cidr", "");
            res.put("nivel_tema", nivel);
            res.put("geo_consulta", payloadGeo);
            historicoStore.registrarConsulta(entrada, res);
        } catch (HistoricoPersistenciaException ex) {
            LOG.warnf("Falha ao registrar histórico geo: %s", ex.getMessage());
        }
    }

    private String normalizarIpTexto(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            InetAddress addr = InetAddress.getByName(raw.strip());
            return addr.getHostAddress();
        } catch (Exception ex) {
            return "";
        }
    }
}
