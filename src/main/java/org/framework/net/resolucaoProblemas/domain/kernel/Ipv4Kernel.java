package org.framework.net.resolucaoProblemas.domain.kernel;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.ipv4.IPv4Address;
import jakarta.enterprise.context.ApplicationScoped;
import org.framework.net.resolucaoProblemas.exception.EntradaInvalidaException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Kernel IPv4 para VLSM/planejamento (biblioteca inet.ipaddr).
 * Complementa {@link org.framework.net.analiseDidatica.domain.kernel.Ipv4Kernel},
 * focado em didática bitwise na análise didática.
 */
@ApplicationScoped
public class Ipv4Kernel {

    public IPv4Address parseNetwork(String input, String fieldLabel) {
        String txt = input == null ? "" : input.strip();
        if (txt.isEmpty()) {
            throw new EntradaInvalidaException(fieldLabel + " deve ser informado.");
        }
        IPAddressString addrString = new IPAddressString(txt);
        if (!addrString.isValid()) {
            throw new EntradaInvalidaException("Rede base invalida: " + txt);
        }
        IPAddress address = addrString.getAddress();
        if (address == null || !address.isIPv4()) {
            throw new EntradaInvalidaException("A rede base deve ser IPv4.");
        }
        return address.toIPv4().toPrefixBlock();
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

    public ClassificacaoIpv4 classificacaoIpv4(int primeiroOcteto) {
        if (primeiroOcteto >= 1 && primeiroOcteto <= 126) {
            return new ClassificacaoIpv4("A", "1-126", "255.0.0.0");
        }
        if (primeiroOcteto >= 128 && primeiroOcteto <= 191) {
            return new ClassificacaoIpv4("B", "128-191", "255.255.0.0");
        }
        if (primeiroOcteto >= 192 && primeiroOcteto <= 223) {
            return new ClassificacaoIpv4("C", "192-223", "255.255.255.0");
        }
        if (primeiroOcteto >= 224 && primeiroOcteto <= 239) {
            return new ClassificacaoIpv4("D", "224-239", "Multicast (sem máscara padrão)");
        }
        return new ClassificacaoIpv4("E", "240-255", "Reservada/Experimental");
    }

    public int requiredPrefixForHosts(int hostCount) {
        int needed = hostCount + 2;
        int hostBits = 32 - Integer.numberOfLeadingZeros(needed - 1);
        return 32 - hostBits;
    }

    public int prefixLength(IPv4Address network) {
        Integer prefix = network.getNetworkPrefixLength();
        return prefix == null ? 32 : prefix;
    }

    public HostRange hostsRange(IPv4Address network) {
        if (network.getCount().compareTo(BigInteger.valueOf(2)) <= 0) {
            return new HostRange(network.getLower().toCanonicalString(), network.getUpper().toCanonicalString());
        }
        IPv4Address firstHost = network.getLower().increment(1).toIPv4().withoutPrefixLength();
        IPv4Address lastHost = network.getUpper().increment(-1).toIPv4().withoutPrefixLength();
        return new HostRange(firstHost.toCanonicalString(), lastHost.toCanonicalString());
    }

    public int hostsSupported(IPv4Address network) {
        BigInteger count = network.getCount();
        if (count.compareTo(BigInteger.valueOf(2)) <= 0) {
            return Math.max(count.intValue() - 2, 0);
        }
        return Math.max(count.intValue() - 2, 0);
    }

    public String netmask(IPv4Address network) {
        return network.getNetworkMask().toCanonicalString();
    }

    public String wildcard(IPv4Address network) {
        return network.getHostMask().toCanonicalString();
    }

    public String gateway(IPv4Address network) {
        return network.getLower().increment(1).withoutPrefixLength().toCanonicalString();
    }

    public List<IPv4Address> usableHosts(IPv4Address network) {
        List<IPv4Address> hosts = new ArrayList<>();
        BigInteger count = network.getCount();
        boolean temRedeEBroadcast = count.compareTo(BigInteger.valueOf(2)) > 0;
        BigInteger redeValor = network.getLower().getValue();
        BigInteger broadcastValor = network.getUpper().getValue();
        Iterator<? extends IPv4Address> it = network.iterator();
        while (it.hasNext()) {
            IPv4Address addr = it.next();
            BigInteger valor = addr.getValue();
            // Em blocos com rede + broadcast (prefixo <= /30) esses dois endereços
            // não são atribuíveis a hosts; em /31 e /32 todos os endereços contam.
            if (temRedeEBroadcast && (valor.equals(redeValor) || valor.equals(broadcastValor))) {
                continue;
            }
            hosts.add(addr.withoutPrefixLength());
        }
        return hosts;
    }

    public Iterator<? extends IPv4Address> iterateSubnets(IPv4Address baseNetwork, int prefix) {
        IPv4Address base = baseNetwork.toPrefixBlock();
        int basePrefix = prefixLength(base);
        if (prefix < basePrefix) {
            throw new EntradaInvalidaException(
                    "Prefixo /" + prefix + " é mais amplo que a rede base /" + basePrefix + ".");
        }
        if (prefix == basePrefix) {
            return List.of(base).iterator();
        }
        IPv4Address subdivided = base.setPrefixLength(prefix, false);
        return subdivided.prefixBlockIterator();
    }

    public boolean overlaps(IPv4Address a, IPv4Address b) {
        return a.overlaps(b);
    }

    public long addressCount(IPv4Address network) {
        return network.getCount().longValue();
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

    public record InferenciaCidr(int cidr, String descricaoOrigem) { }

    public record ClassificacaoIpv4(String classe, String faixaOcteto, String mascaraPadrao) { }

    public record HostRange(String start, String end) { }
}
