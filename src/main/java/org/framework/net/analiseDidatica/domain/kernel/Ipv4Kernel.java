package org.framework.net.analiseDidatica.domain.kernel;

import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.analiseDidatica.exception.EntradaInvalidaException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class Ipv4Kernel {

    private static final long MASK32 = 0xFFFFFFFFL;
    private static final int[] PESOS = {128, 64, 32, 16, 8, 4, 2, 1};

    public Map<String, Object> bannerContextoAnaliseComIp(
            String ipTxt, int cidr, String maskS, String wildcardS,
            String redeS, String broadS, long total, long uties, long pulo) {
        Map<String, Object> banner = new LinkedHashMap<>();
        banner.put("titulo", "Contexto desta análise");
        banner.put("subtitulo",
                "Todos os quadros abaixo (bits, régua, tabela dinâmica, ACL Cisco etc.) "
                        + "referem-se apenas a este cenário.");
        banner.put("itens", List.of(
                item("Prefixo e máscara utilizados",
                        "/" + cidr + " · " + maskS + " · wildcard " + wildcardS),
                item("Cada máscara → um / específico",
                        "O /" + cidr + " vem diretamente desta máscara (" + maskS + "): é o número de bits 1 consecutivos a "
                                + "contar da esquerda. Se a aula usar outra máscara, o / muda e o tamanho de bloco muda — "
                                + "não existe o mesmo / com duas máscaras pontuadas diferentes."),
                item("Sub-rede que contém este IP",
                        "Rede " + redeS + ", broadcast " + broadS + ". "
                                + "O IP informado (" + ipTxt + ") está dentro deste bloco /" + cidr + "."),
                item("Para que serve este / (o que ele “corta” na prática)",
                        "Cada rede /" + cidr + " tem até " + total + " endereços IPv4 no bloco "
                                + "(" + uties + " em geral atribuíveis a hosts). "
                                + "O “pulo” entre sub-redes vizinhas, no desenho que você estiver vendo, costuma ser " + pulo + " "
                                + "no octeto em que a rede muda — isso explica de quanto em quanto a rede “destrava”."),
                item("Na disciplina aparecem vários / — aqui só este cenário",
                        "Na mesma disciplina há exercícios com granulometrias diferentes: enlaces podem aparecer como "
                                + "/30 ou /31; redes maiores como /18 ou /20; LANs típicas como /24; etc. Isso depende sempre "
                                + "do IP e da máscara da questão (ou só da máscara). Esta análise fixa apenas o seu caso atual: "
                                + "/" + cidr + " para este conjunto informado.")
        ));
        return banner;
    }

    public Map<String, Object> bannerContextoAnaliseSoMascara(
            int cidr, String maskS, String wildcardS, long total, long uties, long pulo) {
        Map<String, Object> banner = new LinkedHashMap<>();
        banner.put("titulo", "Contexto desta análise (só máscara, sem IP)");
        banner.put("subtitulo",
                "Você definiu o tamanho de bloco (/ e máscara). Os números abaixo valem para qualquer rede com "
                        + "este prefixo; ainda não há um endereço de rede/broadcast concreto.");
        banner.put("itens", List.of(
                item("Prefixo e máscara", "/" + cidr + " · " + maskS + " · wildcard " + wildcardS),
                item("Cada máscara → um / específico",
                        "Para " + maskS + " o CIDR só pode ser /" + cidr + " (conta de bits 1 da máscara = " + cidr + "). "
                                + "Não se escolhe o / separado da máscara: ele depende dela. Outro desenho de máscara, outro / e outra tabela de intervalos."),
                item("Sub-rede concreta (rede + broadcast)",
                        "Ainda não: informe um endereço IP (modo Máscara ou CIDR) para o framework fechar a sub-rede de exemplo."),
                item("Para que serve este /",
                        "Indica o tamanho de cada pedaço: " + total + " endereços por rede (típicos " + uties + " hosts úteis). "
                                + "O salto entre sub-redes consecutivas, nessa granulometria, costuma ser múltiplo de " + pulo + " no octeto de variação."),
                item("Vários / na aula — um / por máscara de cada vez",
                        "O material percorre muitos prefixos: /30 em ponto a ponto, /18 ou /19 em blocos maiores, /28 em "
                                + "sub-redes pequenas, etc. Cada máscara gera o seu /; esta tela mostra só o "
                                + "que resulta da máscara que acabou de usar: /" + cidr + ". "
                                + "Troque a máscara (ou o IP com outra máscara) e o / e toda a tabela acompanham o novo exercício.")
        ));
        return banner;
    }

    public String notaCidrCisco(int cidr) {
        if (cidr == 8 || cidr == 16 || cidr == 24) {
            return "Máscara padrão classful: /8 (A), /16 (B) ou /24 (C) — o que o material Cisco costuma associar "
                    + "a cada classe de rede base.";
        }
        return "Prefixo /" + cidr + " (CIDR/VLSM): no material Cisco, o estudo de *rede* e *sub-rede* foca nas classes A, B e C; "
                + "máscaras como 255.255.192.0 segmentam blocos com qualquer prefixo válido, sem ser a 'classe' de 8, 16 ou 24 sozinha.";
    }

    public List<Map<String, Object>> referenciaFixaClassesAbc(int o1) {
        boolean selA = o1 >= 1 && o1 <= 126;
        boolean selB = o1 >= 128 && o1 <= 191;
        boolean selC = o1 >= 192 && o1 <= 223;
        List<Map<String, Object>> ref = new ArrayList<>();
        ref.add(cartaoClasse("A", "1 – 126", "/8 · 255.0.0.0", selA));
        ref.add(cartaoClasse("B", "128 – 191", "/16 · 255.255.0.0", selB));
        ref.add(cartaoClasse("C", "192 – 223", "/24 · 255.255.255.0", selC));
        return ref;
    }

    public List<Map<String, Object>> referenciaCartaoUnicoAbc(String letra) {
        if ("E".equals(letra)) {
            return List.of(cartaoClasse("Classe E teórica", "240 – 255", "/4 · 240.0.0.0", true));
        }
        if (!Set.of("A", "B", "C").contains(letra)) {
            return List.of();
        }
        int o1 = switch (letra) {
            case "A" -> 1;
            case "B" -> 128;
            default -> 192;
        };
        for (Map<String, Object> c : referenciaFixaClassesAbc(o1)) {
            if (letra.equals(c.get("letra"))) {
                Map<String, Object> copia = new LinkedHashMap<>(c);
                copia.put("ativo", true);
                return List.of(copia);
            }
        }
        return List.of();
    }

    public String classeReferenciaPorPrefixo(int cidr) {
        if (cidr < 0 || cidr > 32) {
            return null;
        }
        if (cidr == 4) {
            return "E";
        }
        if (cidr == 0) {
            return "A";
        }
        if (cidr >= 1 && cidr <= 8) {
            return "A";
        }
        if (cidr >= 9 && cidr <= 23) {
            return "B";
        }
        return "C";
    }

    public ClasseDidatica classeIpv4Didatica(int o1) {
        if (o1 >= 1 && o1 <= 126) {
            return new ClasseDidatica("A", "1º octeto 1–126 — máscara padrão /8 (255.0.0.0)", null);
        }
        if (o1 >= 128 && o1 <= 191) {
            return new ClasseDidatica("B", "1º octeto 128–191 — máscara padrão /16 (255.255.0.0)", null);
        }
        if (o1 >= 192 && o1 <= 223) {
            return new ClasseDidatica("C", "1º octeto 192–223 — máscara padrão /24 (255.255.255.0)", null);
        }
        if (o1 == 0) {
            return new ClasseDidatica("—", "Fora das faixas A, B e C (unicast do 1º octeto).",
                    "Observação: faixa 0.0.0.0/8 é reservada (não usada como host de produção na Internet).");
        }
        if (o1 == 127) {
            return new ClasseDidatica("—", "Fora das faixas A, B e C (unicast do 1º octeto).",
                    "Observação: 127.0.0.0/8 é loopback (localhost); não é tratada como classe A/B/C aplicável.");
        }
        if (o1 >= 224 && o1 <= 239) {
            return new ClasseDidatica("—", "Fora das faixas A, B e C (unicast do 1º octeto).",
                    "Observação (modelo classful completo): 1º octeto 224–239 = faixa D, "
                            + "endereçamento multicast (não é host unicast).");
        }
        if (o1 >= 240 && o1 <= 255) {
            return new ClasseDidatica("—", "Fora das faixas A, B e C (unicast do 1º octeto).",
                    "Observação (modelo classful completo): 1º octeto 240–255 = faixa E, "
                            + "reservada / experimental (não atribuível a host comum).");
        }
        return new ClasseDidatica("—", "Indefinido para o 1º octeto.", null);
    }

    public String classeVariantCss(String classe) {
        return switch (classe) {
            case "A" -> "a";
            case "B" -> "b";
            case "C" -> "c";
            case "—" -> "outros";
            default -> "outros";
        };
    }

    public PrivacidadeResult privacidadeRfc1918(int[] parts) {
        int o1 = parts[0];
        int o2 = parts[1];
        int o3 = parts[2];
        int o4 = parts[3];
        if (o1 == 0) {
            return new PrivacidadeResult("Especial", "Faixa 0.0.0.0/8 (Rede atual / especial)");
        }
        if (o1 == 255 && o2 == 255 && o3 == 255 && o4 == 255) {
            return new PrivacidadeResult("Broadcast Limitado", "Endereço 255.255.255.255");
        }
        if (o1 == 127) {
            return new PrivacidadeResult("Loopback", "Faixa 127.0.0.0/8 (localhost, teste local)");
        }
        if (o1 == 169 && o2 == 254) {
            return new PrivacidadeResult("APIPA", "Faixa 169.254.0.0/16 (auto-configuração sem DHCP)");
        }
        if (o1 == 10) {
            return new PrivacidadeResult("Privado (RFC 1918)", "Faixa privada 10.0.0.0 - 10.255.255.255");
        }
        if (o1 == 172 && o2 >= 16 && o2 <= 31) {
            return new PrivacidadeResult("Privado (RFC 1918)", "Faixa privada 172.16.0.0 - 172.31.255.255");
        }
        if (o1 == 192 && o2 == 168) {
            return new PrivacidadeResult("Privado (RFC 1918)", "Faixa privada 192.168.0.0 - 192.168.255.255");
        }
        if (o1 >= 224 && o1 <= 239) {
            return new PrivacidadeResult("Multicast", "Faixa 224.0.0.0 - 239.255.255.255 (não host unicast)");
        }
        if (o1 >= 240 && o1 <= 255) {
            return new PrivacidadeResult("Reservado/Experimental", "Faixa 240.0.0.0 - 255.255.255.255");
        }
        return new PrivacidadeResult("Público", "Fora das faixas privadas/locais (roteável conforme políticas)");
    }

    public String fmtIp(long n) {
        n &= MASK32;
        return ((n >> 24) & 255) + "." + ((n >> 16) & 255) + "." + ((n >> 8) & 255) + "." + (n & 255);
    }

    public int[] parseIpv4Parts(String ipS, String nomeCampo) {
        String txt = ipS == null ? "" : ipS.strip();
        if (txt.isEmpty()) {
            throw new EntradaInvalidaException(nomeCampo + " vazio.");
        }
        String[] rawParts = txt.split("\\.");
        if (rawParts.length != 4) {
            throw new EntradaInvalidaException(nomeCampo + " inválido. Use formato x.x.x.x.");
        }
        int[] parts = new int[4];
        for (int idx = 0; idx < rawParts.length; idx++) {
            String raw = rawParts[idx].strip();
            int octetoIdx = idx + 1;
            if (raw.isEmpty()) {
                throw new EntradaInvalidaException(nomeCampo + " inválido: octeto " + octetoIdx + " está vazio.");
            }
            if (!raw.chars().allMatch(Character::isDigit)) {
                throw new EntradaInvalidaException(nomeCampo + " inválido: octeto " + octetoIdx + " não é numérico.");
            }
            int octeto = Integer.parseInt(raw);
            if (octeto < 0 || octeto > 255) {
                throw new EntradaInvalidaException(nomeCampo + " inválido: octeto " + octetoIdx + " fora de 0-255.");
            }
            parts[idx] = octeto;
        }
        return parts;
    }

    public int[] parseIpv4Parts(String ipS) {
        return parseIpv4Parts(ipS, "IP");
    }

    public InferenciaCidr inferirCidrPorIp(String ipS) {
        int[] parts = parseIpv4Parts(ipS, "IP");
        int o1 = parts[0];
        if (o1 == 0) {
            return new InferenciaCidr(8, "Inferido (classful): 0.x.x.x => /8");
        }
        if (o1 >= 1 && o1 <= 126) {
            return new InferenciaCidr(8, "Inferido (classful): classe A => /8");
        }
        if (o1 == 127) {
            return new InferenciaCidr(8, "Inferido (classful): loopback 127.x.x.x => /8");
        }
        if (o1 >= 128 && o1 <= 191) {
            return new InferenciaCidr(16, "Inferido (classful): classe B => /16");
        }
        if (o1 >= 192 && o1 <= 223) {
            return new InferenciaCidr(24, "Inferido (classful): classe C => /24");
        }
        if (o1 >= 224 && o1 <= 239) {
            return new InferenciaCidr(4, "Inferido (classful): classe D (multicast) => /4");
        }
        return new InferenciaCidr(4, "Inferido (classful): classe E (reservada) => /4");
    }

    public Integer mascaraDottedParaCidr(String maskS) {
        try {
            int[] parts = parseIpv4Parts(maskS, "Máscara decimal");
            long val = ((long) parts[0] << 24) + ((long) parts[1] << 16) + ((long) parts[2] << 8) + parts[3];
            val &= MASK32;
            long inv = (~val) & MASK32;
            if (inv != 0 && (inv & (inv + 1)) != 0) {
                return null;
            }
            return Long.bitCount(val);
        } catch (EntradaInvalidaException ex) {
            return null;
        }
    }

    public Integer wildcardDottedParaCidr(String wildS) {
        try {
            int[] parts = parseIpv4Parts(wildS, "Wildcard mask");
            String maskEquivalente = (255 - parts[0]) + "." + (255 - parts[1]) + "."
                    + (255 - parts[2]) + "." + (255 - parts[3]);
            return mascaraDottedParaCidr(maskEquivalente);
        } catch (EntradaInvalidaException ex) {
            return null;
        }
    }

    public Map<String, Object> coreMascara(int cidr) {
        if (cidr < 0 || cidr > 32) {
            return null;
        }
        long mI = (MASK32 << (32 - cidr)) & MASK32;
        long pulo;
        if (cidr > 24) {
            pulo = 1L << (32 - cidr);
        } else if (cidr > 16) {
            pulo = 1L << (24 - cidr);
        } else if (cidr > 8) {
            pulo = 1L << (16 - cidr);
        } else {
            pulo = 1L << (8 - cidr);
        }

        long tamanho = 1L << (32 - cidr);
        long uteis;
        if (cidr == 32) {
            uteis = 1;
        } else if (cidr == 31) {
            uteis = 2;
        } else if (tamanho > 2) {
            uteis = tamanho - 2;
        } else {
            uteis = 0;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mask", fmtIp(mI));
        result.put("wildcard", fmtIp((~mI) & MASK32));
        result.put("bin_raw", String.format("%32s", Long.toBinaryString(mI)).replace(' ', '0'));
        result.put("zeros", 32 - cidr);
        result.put("total", tamanho);
        result.put("uteis", uteis);
        result.put("pulo", pulo);
        result.put("cidr", cidr);
        result.put("_m_i", mI);
        return result;
    }

    public Map<String, Object> temaDinamico(int cidr, long totalIps) {
        if (cidr >= 24) {
            return tema("Baixo risco operacional",
                    "Prefixo mais específico: bloco menor, varredura e governança mais simples.",
                    "#238636", "#3fb950", "#2ea043", "#2ea043", "#3fb950", "#2d6a4f",
                    "#14261c", "#8be9a8", "rgba(63, 185, 80, 0.16)");
        }
        if (cidr >= 17) {
            return tema("Risco moderado",
                    "Rede intermediária: atenção à segmentação e aos controles de acesso.",
                    "#1f6feb", "#79c0ff", "#58a6ff", "#1f6feb", "#79c0ff", "#1f4f87",
                    "#101c2f", "#9fd0ff", "rgba(121, 192, 255, 0.16)");
        }
        if (cidr >= 9) {
            return tema("Risco elevado",
                    "Bloco amplo: recomenda-se dividir sub-redes e restringir superfície.",
                    "#d29922", "#e3b341", "#f2cc60", "#d29922", "#e3b341", "#7a611f",
                    "#2b2310", "#f2d487", "rgba(227, 179, 65, 0.16)");
        }
        return tema("Risco crítico",
                "Rede muito extensa: alto impacto operacional e maior exposição para varredura.",
                "#f85149", "#ff7b72", "#ff7b72", "#f85149", "#ff7b72", "#8b2f2a",
                "#2f1414", "#ffb3ad", "rgba(248, 81, 73, 0.18)");
    }

    public TabelaReferenciaSubredes tabelaReferenciaSubredes(int cidr) {
        if (cidr < 0 || cidr > 32) {
            return new TabelaReferenciaSubredes(4, List.of());
        }

        int octeto;
        if (cidr <= 8) {
            octeto = 1;
        } else if (cidr <= 16) {
            octeto = 2;
        } else if (cidr <= 24) {
            octeto = 3;
        } else {
            octeto = 4;
        }

        int start = (octeto - 1) * 8 + 1;
        int end = octeto * 8;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int barra = start; barra <= end; barra++) {
            int bitsOn = barra - ((octeto - 1) * 8);
            List<Integer> bits = new ArrayList<>(8);
            for (int i = 0; i < 8; i++) {
                bits.add(i < bitsOn ? 1 : 0);
            }
            int mascaraOcteto = 0;
            for (int i = 0; i < bitsOn; i++) {
                mascaraOcteto += PESOS[i];
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("barra", barra);
            row.put("bits", bits);
            row.put("intervalos", 1L << bitsOn);
            row.put("variacao", 1L << (8 - bitsOn));
            row.put("ips", 1L << (32 - barra));
            row.put("mascara_octeto", mascaraOcteto);
            row.put("is_current", barra == cidr);
            rows.add(row);
        }
        return new TabelaReferenciaSubredes(octeto, rows);
    }

    public TabelaConversaoBits tabelaConversaoBits(int cidr) {
        List<Map<String, String>> linhas = List.of(
                linhaConversao("1 bit", "1", "0.125", "1", "1"),
                linhaConversao("1 nibble", "4", "0.5", "1111", "15"),
                linhaConversao("1 byte (octeto)", "8", "1", "11111111", "255"),
                linhaConversao("1 word", "16", "2", "11111111 11111111", "65535"),
                linhaConversao("1 dword (IPv4)", "32", "4", "32 bits em 1", "4294967295"),
                linhaConversao("1 KiB", "8192", "1024", "2^10 bytes", "1024"),
                linhaConversao("1 MiB", "8388608", "1048576", "2^20 bytes", "1048576")
        );

        int hostBits = 32 - cidr;
        int redeBits = cidr;
        long capacidade = 1L << hostBits;
        List<Map<String, Object>> conversaoAtual = List.of(
                conversaoAtual("Bits de rede", redeBits),
                conversaoAtual("Bits de host", hostBits),
                conversaoAtual("Bits totais IPv4", 32),
                conversaoAtual("Bytes totais IPv4", "4"),
                conversaoAtual("Capacidade do bloco", "2^" + hostBits + " = " + capacidade + " IPs"),
                conversaoAtual("Conversão útil", "1 byte = 8 bits | 1 nibble = 4 bits")
        );
        return new TabelaConversaoBits(linhas, conversaoAtual);
    }

    public Map<String, Object> processarSomenteMascara(int cidr) {
        Map<String, Object> c = coreMascara(cidr);
        if (c == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>(c);
        out.remove("_m_i");

        TabelaReferenciaSubredes ref = tabelaReferenciaSubredes(cidr);
        int octetoRef = ref.octeto();
        List<Map<String, Object>> tabelaRef = ref.rows();
        TabelaConversaoBits conv = tabelaConversaoBits(cidr);
        Map<String, Object> tema = temaDinamico(cidr, (Long) c.get("total"));

        out.put("somente_mascara", true);
        out.put("contexto_didatico", "prefixo_subrede");
        out.put("cidr_origem", "");
        out.put("octeto_referencia", octetoRef);
        out.put("tabela_referencia", tabelaRef);
        out.put("tabela_conversao_bits", conv.linhas());
        out.put("conversao_atual", conv.conversaoAtual());
        out.putAll(tema);
        out.put("rede", "—");
        out.put("broad", "—");
        out.put("primeiro_host", "—");
        out.put("ultimo_host", "—");
        out.put("nota_cidr_cisco", notaCidrCisco(cidr));

        String letraRef = classeReferenciaPorPrefixo(cidr);
        out.put("classes_abc_fixas", letraRef != null ? referenciaCartaoUnicoAbc(letraRef) : List.of());

        if (cidr == 4) {
            out.put("classe_observacao",
                    "Representa teoricamente o bloco 240.0.0.0/4, associado à faixa Classe E/reservada: "
                            + "240.0.0.0 até 255.255.255.255. Não é usado como máscara comum em redes locais.");
        } else {
            out.put("classe_observacao",
                    "Foco em aula: o que importa é o / (barra) e a máscara no quadro; o cartão mostra só a referência A/B/C "
                            + "que costuma acompanhar esse prefixo no material (ex.: /18 → B).");
        }
        out.put("classe", letraRef != null ? letraRef : "—");
        out.put("classe_faixa",
                "Prefixo em estudo: /" + cidr + " com máscara " + out.get("mask")
                        + " — acompanhe só essa barra na conversão binária e na tabela.");
        out.put("classe_variant", letraRef != null ? classeVariantCss(letraRef) : "outros");
        out.put("primeiro_octeto", null);
        out.put("banner_contexto", bannerContextoAnaliseSoMascara(
                cidr,
                (String) out.get("mask"),
                (String) out.get("wildcard"),
                (Long) c.get("total"),
                (Long) c.get("uteis"),
                (Long) c.get("pulo")
        ));
        out.put("ip_informado", null);
        out.put("enunciado_prova", enunciadoProvaIntervalos(
                cidr, (Long) c.get("pulo"), (Long) c.get("total"), (Long) c.get("uteis"), octetoRef));
        out.put("texto_copia",
                "Máscara: " + c.get("mask") + "\n"
                        + "CIDR: /" + cidr + "\n"
                        + "Wildcard: " + c.get("wildcard") + "\n"
                        + "Total de hosts (bloco): " + c.get("total") + "\n"
                        + "Hosts úteis: " + c.get("uteis"));
        return out;
    }

    public HostsSubrede hostsDaSubrede(long redeI, long broadI, int cidr) {
        if (cidr == 32) {
            String host = fmtIp(redeI);
            return new HostsSubrede(host, host);
        }
        if (cidr == 31) {
            return new HostsSubrede(fmtIp(redeI), fmtIp(redeI + 1));
        }
        if (broadI - redeI >= 2) {
            return new HostsSubrede(fmtIp(redeI + 1), fmtIp(broadI - 1));
        }
        return new HostsSubrede("—", "—");
    }

    public PapelIpNoBloco papelIpNoBloco(long ipI, long redeI, long broadI, int cidr) {
        if (cidr == 32) {
            return new PapelIpNoBloco("Host único (/32)", "");
        }
        if (cidr == 31) {
            return new PapelIpNoBloco("Host válido em /31 (enlace P2P)", "Em /31 os dois endereços são utilizáveis (RFC 3021).");
        }
        if (ipI == redeI) {
            return new PapelIpNoBloco("Endereço de rede", "Este IP identifica a sub-rede e não deve ser atribuído a host.");
        }
        if (ipI == broadI) {
            return new PapelIpNoBloco("Endereço de broadcast", "Este IP é reservado para broadcast e não deve ser atribuído a host.");
        }
        return new PapelIpNoBloco("Host válido", "");
    }

    public Map<String, Object> enunciadoProvaIntervalos(int cidr, long pulo, long total, long uteis, int octetoReferencia) {
        long qtdeIntervalos = pulo > 0 ? Math.max(1, 256 / pulo) : 1;
        String nota = "";
        if (cidr == 31) {
            nota = "Em /31 (RFC 3021) os dois endereços do bloco costumam ser usáveis em enlace ponto a ponto; "
                    + "a regra clássica 2^n−2 de “rede + broadcast” não se aplica do mesmo modo.";
        } else if (cidr == 32) {
            nota = "Em /32 há um único endereço; não há subtração de rede e broadcast em sub-rede com 1 IP.";
        }

        String endCli = total == 1 ? "endereço" : "endereços";
        String fraseEstiloQuadro = qtdeIntervalos + " intervalos que variam de " + pulo + " em " + pulo
                + " no " + octetoReferencia + "º octeto; "
                + "cada intervalo comporta " + total + " " + endCli + " no bloco";
        if (uteis != total) {
            fraseEstiloQuadro += " (" + uteis + " em geral atribuíveis a hosts, descontando rede e broadcast).";
        } else {
            fraseEstiloQuadro += ".";
        }

        Integer eqQ = potenciaDe2Expoente(qtdeIntervalos);
        Integer eqP = potenciaDe2Expoente(pulo);
        Integer eqIps = potenciaDe2Expoente(total);
        List<String> partesQuadro = new ArrayList<>();
        if (eqQ != null && eqP != null) {
            partesQuadro.add("Potências (estilo quadro / slide): 2^" + eqQ + " = " + qtdeIntervalos
                    + " (intervalos no " + octetoReferencia + "º octeto); "
                    + "2^" + eqP + " = " + pulo + " (variação / salto entre redes consecutivas nesse octeto).");
        }
        if (eqIps != null && total > 0) {
            partesQuadro.add(" Cada intervalo: 2^" + eqIps + " = " + total
                    + " endereços no bloco (como no enunciado “IPs disponíveis” no total do bloco).");
        }
        String linhaPotenciasQuadro = String.join("", partesQuadro).strip();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("qtde_intervalos", qtdeIntervalos);
        result.put("variacao", pulo);
        result.put("ips_total_por_subrede", total);
        result.put("ips_uteis_por_subrede", uteis);
        result.put("octeto_referencia", octetoReferencia);
        result.put("nota", nota);
        result.put("frase_estilo_quadro", fraseEstiloQuadro);
        result.put("linha_potencias_quadro", linhaPotenciasQuadro);
        return result;
    }

    public Map<String, Object> resumoAberturaIntervalos(
            int cidr, long pulo, long total, long uteis, String rede, String broad,
            int octetoReferencia, List<Map<String, Object>> proximasSubredes) {
        long intervalosNoOcteto = pulo > 0 ? Math.max(1, 256 / pulo) : 1;
        List<Map<String, String>> exemplos = new ArrayList<>();
        int limite = Math.min(4, proximasSubredes.size());
        for (int i = 0; i < limite; i++) {
            Map<String, Object> s = proximasSubredes.get(i);
            Map<String, String> ex = new LinkedHashMap<>();
            ex.put("nome", (String) s.get("nome"));
            ex.put("faixa", s.get("rede") + " até " + s.get("broadcast"));
            exemplos.add(ex);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intervalos_no_octeto", intervalosNoOcteto);
        result.put("pulo", pulo);
        result.put("ips_por_bloco", total);
        result.put("uteis_por_bloco", uteis);
        result.put("rede_atual", rede);
        result.put("broadcast_atual", broad);
        result.put("octeto_referencia", octetoReferencia);
        result.put("titulo", intervalosNoOcteto + " intervalos variam de " + pulo + " em " + pulo
                + " no octeto " + octetoReferencia);
        result.put("faixa_atual", rede + " até " + broad + "/" + cidr);
        result.put("exemplos", exemplos);
        return result;
    }

    public Map<String, Object> processar(String ipS, int cidr) {
        return processar(ipS, cidr, 5);
    }

    public Map<String, Object> processar(String ipS, int cidr, int reguaCount) {
        Map<String, Object> c = coreMascara(cidr);
        if (c == null) {
            throw new EntradaInvalidaException("CIDR inválido para cálculo de rede.");
        }

        int[] parts = parseIpv4Parts(ipS, "IP");
        long mI = (Long) c.get("_m_i");
        long ipI = ((long) parts[0] << 24) + ((long) parts[1] << 16) + ((long) parts[2] << 8) + parts[3];
        long rI = ipI & mI;
        long bI = rI | (MASK32 ^ mI);
        long tamanho = (Long) c.get("total");
        HostsSubrede hosts = hostsDaSubrede(rI, bI, cidr);
        String primeiroHost = hosts.primeiro();
        String ultimoHost = hosts.ultimo();

        ClasseDidatica classeInfo = classeIpv4Didatica(parts[0]);
        String classe = classeInfo.classe();
        List<Map<String, Object>> classesAbcFixas = Set.of("A", "B", "C").contains(classe)
                ? referenciaCartaoUnicoAbc(classe)
                : List.of();
        PrivacidadeResult privacidade = privacidadeRfc1918(parts);
        String ipTipoPrivacidade = privacidade.tipo();
        String ipFaixaPrivacidade = privacidade.faixa();
        boolean hostsRecomendados = !Set.of("Multicast", "Reservado/Experimental").contains(ipTipoPrivacidade);

        List<Map<String, Object>> andTable = new ArrayList<>();
        for (int octIdx = 0; octIdx < 4; octIdx++) {
            int shift = 24 - (octIdx * 8);
            int ipOct = (int) ((ipI >> shift) & 255);
            int maskOct = (int) ((mI >> shift) & 255);
            int andOct = ipOct & maskOct;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ip_oct", ipOct);
            row.put("mask_oct", maskOct);
            row.put("and_oct", andOct);
            row.put("wild_oct", 255 - maskOct);
            row.put("ip_bin", String.format("%8s", Integer.toBinaryString(ipOct)).replace(' ', '0'));
            row.put("mask_bin", String.format("%8s", Integer.toBinaryString(maskOct)).replace(' ', '0'));
            row.put("and_bin", String.format("%8s", Integer.toBinaryString(andOct)).replace(' ', '0'));
            andTable.add(row);
        }

        List<Map<String, Object>> proximasSubredes = new ArrayList<>();
        for (int i = 0; i < reguaCount; i++) {
            long prox = rI + (i * tamanho);
            if (prox > MASK32) {
                break;
            }
            long broadProx = Math.min(prox + tamanho - 1, MASK32);
            HostsSubrede proxHosts = hostsDaSubrede(prox, broadProx, cidr);
            Map<String, Object> subnet = new LinkedHashMap<>();
            subnet.put("nome", "Subnet " + (i + 1));
            subnet.put("rede", fmtIp(prox));
            subnet.put("primeiro_host", proxHosts.primeiro());
            subnet.put("ultimo_host", proxHosts.ultimo());
            subnet.put("broadcast", fmtIp(broadProx));
            proximasSubredes.add(subnet);
        }

        PapelIpNoBloco papel = papelIpNoBloco(ipI, rI, bI, cidr);
        String ipPapel = papel.papel();
        String ipPapelAlerta = papel.alerta();

        TabelaReferenciaSubredes ref = tabelaReferenciaSubredes(cidr);
        int octetoRef = ref.octeto();
        List<Map<String, Object>> tabelaRef = ref.rows();
        TabelaConversaoBits conv = tabelaConversaoBits(cidr);
        Map<String, Object> aberturaIntervalos = resumoAberturaIntervalos(
                cidr, (Long) c.get("pulo"), (Long) c.get("total"), (Long) c.get("uteis"),
                fmtIp(rI), fmtIp(bI), octetoRef, proximasSubredes);
        Map<String, Object> tema = temaDinamico(cidr, (Long) c.get("total"));

        Map<String, Object> out = new LinkedHashMap<>(c);
        out.remove("_m_i");

        String gatewaySugerido = hostsRecomendados ? primeiroHost : "N/A para este tipo de IP";
        String gatewayAlternativo = hostsRecomendados ? ultimoHost : "N/A para este tipo de IP";
        String resumoProva = "IP informado: " + fmtIp(ipI) + "\n"
                + "Máscara/CIDR: " + c.get("mask") + " /" + cidr + "\n"
                + "Rede: " + fmtIp(rI) + "\n"
                + "Broadcast: " + fmtIp(bI) + "\n"
                + "Wildcard: " + c.get("wildcard") + "\n"
                + "Hosts úteis: " + c.get("uteis") + "\n"
                + "Tipo de IP: " + ipTipoPrivacidade + "\n"
                + "Papel do IP: " + ipPapel + "\n"
                + "Gateway sugerido: " + gatewaySugerido;

        List<Map<String, Object>> resumoProvaItens = List.of(
                resumoItem("🌐 IP informado", fmtIp(ipI)),
                resumoItem("📏 Máscara/CIDR", c.get("mask") + " /" + cidr),
                resumoItem("🧭 Rede", fmtIp(rI)),
                resumoItem("📣 Broadcast", fmtIp(bI)),
                resumoItem("🧩 Wildcard", (String) c.get("wildcard")),
                resumoItem("✅ Hosts úteis", c.get("uteis")),
                resumoItem("🔐 Tipo de IP", ipTipoPrivacidade),
                resumoItem("📌 Papel do IP", ipPapel),
                resumoItem("🚪 Gateway sugerido", gatewaySugerido)
        );

        List<Map<String, Object>> segurancaDicas = montarSegurancaDicas(
                cidr, c, ipPapel, ipTipoPrivacidade);

        out.put("seguranca_dicas", segurancaDicas);
        out.put("somente_mascara", false);
        out.put("contexto_didatico", "ip_host");
        out.put("cidr_origem", "");
        out.put("rede", fmtIp(rI));
        out.put("broad", fmtIp(bI));
        out.put("primeiro_host", primeiroHost);
        out.put("ultimo_host", ultimoHost);
        out.put("classe", classe);
        out.put("classe_faixa", classeInfo.classeFaixa());
        out.put("classe_observacao", classeInfo.classeObservacao());
        out.put("classes_abc_fixas", classesAbcFixas);
        out.put("classe_variant", classeVariantCss(classe));
        out.put("primeiro_octeto", parts[0]);
        out.put("ip_tipo_privacidade", ipTipoPrivacidade);
        out.put("ip_faixa_privacidade", ipFaixaPrivacidade);
        out.put("and_table", andTable);
        out.put("proximas_subredes", proximasSubredes);
        out.put("dns_info", "Servidor de nomes (ex.: 8.8.8.8, 1.1.1.1 ou DNS interno)");
        out.put("dhcp_info", "Faixa dinâmica sugerida: " + primeiroHost + " até " + ultimoHost);
        out.put("vlan_info", "Segmentação lógica de rede (o ID da VLAN não vem do IP/máscara)");
        out.put("wan_info", "Conexão de longa distância/Internet; normalmente usa IP público");
        out.put("gateway_sugerido", gatewaySugerido);
        out.put("gateway_alternativo", gatewayAlternativo);
        out.put("hosts_recomendados", hostsRecomendados);
        out.put("ip_binario_completo", String.format("%8s", Integer.toBinaryString(parts[0])).replace(' ', '0') + "."
                + String.format("%8s", Integer.toBinaryString(parts[1])).replace(' ', '0') + "."
                + String.format("%8s", Integer.toBinaryString(parts[2])).replace(' ', '0') + "."
                + String.format("%8s", Integer.toBinaryString(parts[3])).replace(' ', '0'));
        out.put("ip_papel", ipPapel);
        out.put("ip_papel_alerta", ipPapelAlerta);
        out.put("regua_count", reguaCount);
        out.put("resumo_prova", resumoProva);
        out.put("resumo_prova_itens", resumoProvaItens);
        out.put("octeto_referencia", octetoRef);
        out.put("abertura_intervalos", aberturaIntervalos);
        out.put("tabela_referencia", tabelaRef);
        out.put("tabela_conversao_bits", conv.linhas());
        out.put("conversao_atual", conv.conversaoAtual());
        out.put("cisco_cli",
                "conf t\n"
                        + "interface g0/0\n"
                        + "ip address " + primeiroHost + " " + c.get("mask") + "\n"
                        + "no shutdown");
        out.put("cisco_eigrp_exemplo",
                "router eigrp 100\n"
                        + " network " + fmtIp(rI) + " " + c.get("wildcard") + "\n"
                        + " no auto-summary");
        out.put("cisco_ospf_exemplo",
                "router ospf 1\n"
                        + " network " + fmtIp(rI) + " " + c.get("wildcard") + " area 0");
        out.put("cisco_roteamento_nota",
                "Em EIGRP e OSPFv2 (IOS), o comando network associa interfaces ao processo de roteamento usando "
                        + "endereço de rede + wildcard (não a máscara decimal). O IOS verifica se o IP de cada interface casa "
                        + "com esse par. Use o Network ID e a wildcard do mesmo prefixo que estás a estudar — os valores "
                        + "abaixo coincidem com a rede e wildcard calculados nesta página.");
        out.put("nota_cidr_cisco", notaCidrCisco(cidr));
        out.put("banner_contexto", bannerContextoAnaliseComIp(
                fmtIp(ipI), cidr, (String) c.get("mask"), (String) c.get("wildcard"),
                fmtIp(rI), fmtIp(bI), (Long) c.get("total"), (Long) c.get("uteis"), (Long) c.get("pulo")));
        out.put("ip_informado", fmtIp(ipI));
        out.put("texto_copia",
                "IP analisado: " + fmtIp(ipI) + "\n"
                        + "CIDR: /" + cidr + "\n"
                        + "Máscara: " + c.get("mask") + "\n"
                        + "Wildcard: " + c.get("wildcard") + "\n"
                        + "Rede: " + fmtIp(rI) + "\n"
                        + "Broadcast: " + fmtIp(bI) + "\n"
                        + "Hosts válidos: " + primeiroHost + " até " + ultimoHost + "\n"
                        + "Total de hosts: " + c.get("total") + "\n"
                        + "RFC1918: " + (ipTipoPrivacidade.contains("Privado") ? "Sim" : "Não") + "\n"
                        + "\nReferência Cisco (EIGRP/OSPF):\n"
                        + "  network " + fmtIp(rI) + " " + c.get("wildcard"));
        out.putAll(tema);
        return out;
    }

    private List<Map<String, Object>> montarSegurancaDicas(
            int cidr, Map<String, Object> c, String ipPapel, String ipTipoPrivacidade) {
        List<Map<String, Object>> dicas = new ArrayList<>();
        long uteis = (Long) c.get("uteis");
        long total = (Long) c.get("total");

        if (uteis > 0) {
            if (cidr <= 16) {
                dicas.add(dica("warning", "⚠️",
                        "Aviso de Scan Nmap: Um scan completo de todas as portas para " + total + " IPs "
                                + "exigiria um tempo excessivo. É recomendado particionar essa rede para varreduras mais eficientes."));
            } else if (cidr >= 24) {
                dicas.add(dica("success", "✅",
                        "Superfície de Ataque: Uma rede /" + cidr + " (" + total + " IPs) possui superfície reduzida "
                                + "e varreduras com Nmap são rápidas e controladas."));
            }
        }

        if ("Endereço de broadcast".equals(ipPapel)) {
            dicas.add(dica("danger", "🚨",
                    "Aviso Smurf Attack: IPs de broadcast não devem responder a pacotes ICMP Request "
                            + "(ping), para evitar amplificação em ataques DDoS."));
        }

        switch (ipTipoPrivacidade) {
            case "Reservado/Experimental" -> dicas.add(dica("danger", "🚫",
                    "Endereço Classe E / Reservado. Não deve ser usado para hosts comuns em LAN ou WAN."));
            case "Multicast" -> dicas.add(dica("info", "📡",
                    "Endereço Classe D / Multicast. Usado para transmitir tráfego para múltiplos hosts simultaneamente (ex: OSPF, IPTV)."));
            case "Loopback" -> dicas.add(dica("info", "🔁",
                    "Endereço de Loopback. Usado para testar a pilha TCP/IP local no próprio dispositivo."));
            case "APIPA" -> dicas.add(dica("warning", "⚠️",
                    "Link-Local / APIPA. Ocorre quando o dispositivo falha em obter IP via DHCP."));
            case "Especial" -> dicas.add(dica("warning", "🔍",
                    "Rede Especial (0.x.x.x). Usado como rede atual ou default route (0.0.0.0), não aplicável como host normal."));
            case "Broadcast Limitado" -> dicas.add(dica("danger", "📣",
                    "Broadcast Limitado (255.255.255.255). Envia pacotes a todos os hosts da mesma rede local, não é roteado além do roteador."));
            default -> { }
        }

        if (!ipTipoPrivacidade.contains("Privado")
                && !Set.of("Loopback", "APIPA", "Multicast", "Reservado/Experimental", "—").contains(ipTipoPrivacidade)) {
            dicas.add(dica("primary", "🌍",
                    "Aviso de Borda: Este é um IP Público. Pode estar voltado à internet. "
                            + "Garanta que o Firewall esteja setado como Inbound Deny All por padrão."));
        } else if ("Endereço de rede".equals(ipPapel)) {
            dicas.add(dica("info", "ℹ️",
                    "Network ID: Usado nas tabelas de roteamento. Scans direcionados para este IP "
                            + "não mapeiam hosts internos ativos diretamente."));
        }
        return dicas;
    }

    private static Integer potenciaDe2Expoente(long n) {
        if (n <= 0 || (n & (n - 1)) != 0) {
            return null;
        }
        return 63 - Long.numberOfLeadingZeros(n);
    }

    private static Map<String, Object> item(String rotulo, String valor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rotulo", rotulo);
        m.put("valor", valor);
        return m;
    }

    private static Map<String, Object> cartaoClasse(String letra, String faixa, String mascara, boolean ativo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("letra", letra);
        m.put("faixa_octeto", faixa);
        m.put("mascara_padrao", mascara);
        m.put("ativo", ativo);
        return m;
    }

    private static Map<String, String> linhaConversao(String ref, String bits, String bytes, String bin, String dec) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("referencia", ref);
        m.put("bits", bits);
        m.put("bytes", bytes);
        m.put("binario", bin);
        m.put("decimal", dec);
        return m;
    }

    private static Map<String, Object> conversaoAtual(String chave, Object valor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chave", chave);
        m.put("valor", valor);
        return m;
    }

    private static Map<String, Object> resumoItem(String campo, Object valor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("campo", campo);
        m.put("valor", valor);
        return m;
    }

    private static Map<String, Object> dica(String tipo, String icon, String texto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("icon", icon);
        m.put("texto", texto);
        return m;
    }

    private static Map<String, Object> tema(
            String nivel, String descricao,
            String corBit1, String corResultado, String corPulo, String corBordaResumo,
            String corAcento, String corBordaTabela, String corCabBg, String corCabTexto, String corLinha) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cor_bit1", corBit1);
        m.put("cor_resultado_and", corResultado);
        m.put("cor_pulo", corPulo);
        m.put("cor_borda_resumo", corBordaResumo);
        m.put("cor_acento", corAcento);
        m.put("cor_borda_tabela", corBordaTabela);
        m.put("cor_cabecalho_tabela_bg", corCabBg);
        m.put("cor_cabecalho_tabela_texto", corCabTexto);
        m.put("cor_linha_destaque", corLinha);
        m.put("nivel_tema", nivel);
        m.put("nivel_tema_descricao", descricao);
        return m;
    }

    public record ClasseDidatica(String classe, String classeFaixa, String classeObservacao) { }

    public record PrivacidadeResult(String tipo, String faixa) { }

    public record InferenciaCidr(int cidr, String descricaoOrigem) { }

    public record HostsSubrede(String primeiro, String ultimo) { }

    public record PapelIpNoBloco(String papel, String alerta) { }

    public record TabelaReferenciaSubredes(int octeto, List<Map<String, Object>> rows) { }

    public record TabelaConversaoBits(List<Map<String, String>> linhas, List<Map<String, Object>> conversaoAtual) { }
}
