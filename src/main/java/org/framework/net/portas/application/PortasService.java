package org.framework.net.portas.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.framework.net.portas.domain.PortaItem;
import org.framework.net.portas.domain.PortasCatalog;
import org.framework.net.telemetria.TelemetriaLogger;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class PortasService {

    private static final String FALLBACK_ALTERNATIVA =
            "Aplicar segmentacao, firewall e criptografia ponta a ponta";

    private static final Map<String, String> ALTERNATIVAS_POR_SERVICO = Map.ofEntries(
            Map.entry("ftp", "SFTP ou FTPS"),
            Map.entry("telnet", "SSH"),
            Map.entry("http", "HTTPS (TLS)"),
            Map.entry("http alternativo", "HTTPS (443/8443)"),
            Map.entry("pop3", "POP3S (995) ou IMAPS (993)"),
            Map.entry("imap", "IMAPS (993)"),
            Map.entry("ldap", "LDAPS (636)"),
            Map.entry("netbios", "SMBv3 restrito + VPN"),
            Map.entry("smb", "SMB assinado via VPN"),
            Map.entry("rdp", "Acesso via VPN + MFA"),
            Map.entry("vnc", "VNC via tunel SSH"),
            Map.entry("sql server", "Acesso privado via VPN/bastion"),
            Map.entry("oracle", "Acesso privado + criptografia"),
            Map.entry("mysql", "Acesso privado via VPN/bastion"),
            Map.entry("postgresql", "Acesso privado + SSL"),
            Map.entry("redis", "Redis local com auth e TLS"),
            Map.entry("elasticsearch", "Acesso privado com auth e TLS"),
            Map.entry("mongodb", "Acesso privado com auth e TLS"),
            Map.entry("dns", "DNS restrito + DNSSEC (quando aplicavel)"),
            Map.entry("dhcp", "DHCP Snooping + segmentacao VLAN"),
            Map.entry("ntp", "NTP autenticado e restrito"),
            Map.entry("https", "Manter TLS 1.2/1.3 e certificados validos"),
            Map.entry("https alternativo", "Manter TLS 1.2/1.3 e certificados validos"),
            Map.entry("smtps", "MTA-STS + TLS forte"),
            Map.entry("submission", "Submission 587 com STARTTLS obrigatorio"),
            Map.entry("imaps", "IMAPS com TLS forte"),
            Map.entry("pop3s", "POP3S com TLS forte"),
            Map.entry("ssh", "SSH com chave publica e MFA"),
            Map.entry("tftp", "SFTP/HTTPS para transferencia segura"));

    @Inject
    PortasCatalog portasCatalog;

    @Inject
    TelemetriaLogger telemetriaLogger;

    public List<PortaItemExibicao> montarPortasCatalogoExibicao() {
        telemetriaLogger.logEvent("info", "portas", "catalog_load",
                Map.of("total", portasCatalog.getCatalogo().size()));
        return portasCatalog.getCatalogo().stream()
                .map(item -> PortaItemExibicao.from(item, alternativaSeguraPorta(item)))
                .collect(Collectors.toList());
    }

    private String alternativaSeguraPorta(PortaItem item) {
        String servico = normalizar(item.servico()).toLowerCase(Locale.ROOT);
        String mapeada = ALTERNATIVAS_POR_SERVICO.get(servico);
        if (mapeada != null) {
            return mapeada;
        }
        String recomendacao = normalizar(item.recomendacao());
        if (!recomendacao.isEmpty()) {
            return recomendacao;
        }
        return FALLBACK_ALTERNATIVA;
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
