package org.framework.net.telemetria.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.redis.datasource.RedisDataSource;
import io.vertx.mutiny.redis.client.Response;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.framework.net.telemetria.TelemetriaEvent;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Janela de telemetria em um Redis Stream.
 *
 * <p><b>Propósito de negócio:</b> ampliar a janela que o painel consegue mostrar
 * sem inchar a memória do processo. Hoje a aplicação guarda 500 eventos no heap e
 * perde a janela a cada deploy; o Stream mantém dezenas de milhares fora da JVM e
 * sobrevive ao restart do container. Este projeto já pagou caro por consumo de
 * memória e I/O de telemetria — a intenção aqui é aumentar o alcance sem repetir
 * aquele custo.</p>
 *
 * <p><b>Invariantes do domínio:</b> o Stream é <b>camada de leitura</b>, nunca a
 * fonte durável. A verdade continua sendo o arquivo JSONL em disco, porque um
 * {@code docker compose down -v} apaga o volume do Redis e levaria a telemetria
 * inteira junto. Por isso a escrita aqui é <b>best-effort</b>: acontece depois do
 * arquivo e jamais interrompe o registro do evento. Também não há
 * <i>consumer group</i>: ele existe para entregar a vários processos com garantia,
 * e aqui produtor e consumidor são o mesmo processo — seria estrutura para um
 * problema que não temos.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> degrada em silêncio, como o
 * {@code CacheDistribuido} já faz. Redis fora, comando recusado ou resposta
 * inesperada devolvem {@code false}/lista vazia, e quem chama volta para a janela
 * em memória. Telemetria é observabilidade: ela não pode derrubar a requisição que
 * está tentando observar.</p>
 */
@ApplicationScoped
public class TelemetriaStreamRedis {

    private static final Logger LOG = Logger.getLogger(TelemetriaStreamRedis.class);
    private static final String CAMPO = "json";

    @ConfigProperty(name = "framework.telemetry.stream.enabled", defaultValue = "true")
    boolean habilitado;

    @ConfigProperty(name = "framework.telemetry.stream.chave", defaultValue = "framework-net:telemetria")
    String chave;

    /** Teto aproximado de entradas: o {@code ~} deixa o Redis podar em bloco, que é barato. */
    @ConfigProperty(name = "framework.telemetry.stream.max-len", defaultValue = "50000")
    long maxLen;

    @Inject
    Instance<RedisDataSource> redisDataSource;

    @Inject
    ObjectMapper objectMapper;

    private volatile Boolean disponivel;

    /** O Stream está utilizável agora? Resolvido uma vez e memorizado. */
    public boolean ativo() {
        if (!habilitado) {
            return false;
        }
        Boolean cache = disponivel;
        if (cache != null) {
            return cache;
        }
        synchronized (this) {
            if (disponivel == null) {
                disponivel = testar();
                LOG.infof("Stream de telemetria no Redis: %s (chave=%s, maxlen=%d)",
                        disponivel ? "ativo" : "indisponivel", chave, maxLen);
            }
            return disponivel;
        }
    }

    private boolean testar() {
        try {
            if (!redisDataSource.isResolvable()) {
                return false;
            }
            redisDataSource.get().execute("PING");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Acrescenta o evento à janela.
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@code false} sem lançar e
     * sem log por evento — falha de telemetria não pode virar tempestade de log.
     * A primeira indisponibilidade já foi registrada em {@link #ativo()}.</p>
     */
    public boolean publicar(TelemetriaEvent evento) {
        if (!ativo() || evento == null) {
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(evento);
            redisDataSource.get().execute("XADD", chave,
                    "MAXLEN", "~", String.valueOf(maxLen), "*", CAMPO, json);
            return true;
        } catch (Exception ex) {
            disponivel = false;
            LOG.warnf("Stream de telemetria desativado apos falha: %s", ex.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Últimos eventos da janela, do mais recente para o mais antigo.
     *
     * <p><b>Invariantes do domínio:</b> a ordem devolvida é a mesma da janela em
     * memória (mais novo primeiro), para que trocar a fonte não mude o que o painel
     * mostra.</p>
     *
     * <p><b>Comportamento em caso de falha:</b> lista vazia — quem chama trata
     * vazio como "usar a janela em memória", nunca como "não há eventos".</p>
     */
    public List<TelemetriaEvent> ultimos(int limite) {
        if (!ativo() || limite <= 0) {
            return List.of();
        }
        try {
            Response resposta = redisDataSource.get().execute("XREVRANGE", chave, "+", "-",
                    "COUNT", String.valueOf(limite));
            return converter(resposta);
        } catch (Exception ex) {
            disponivel = false;
            LOG.warnf("Leitura do stream de telemetria falhou: %s", ex.getClass().getSimpleName());
            return List.of();
        }
    }

    /** Quantas entradas o Stream guarda agora; -1 quando indisponível. */
    public long tamanho() {
        if (!ativo()) {
            return -1;
        }
        try {
            Response r = redisDataSource.get().execute("XLEN", chave);
            return r == null ? -1 : r.toLong();
        } catch (Exception ex) {
            return -1;
        }
    }

    /**
     * Converte a resposta do {@code XREVRANGE} em eventos.
     *
     * <p><b>Invariantes do domínio:</b> entrada que não puder ser desserializada é
     * <b>pulada</b>, não interrompe a leitura inteira — um registro corrompido não
     * pode apagar o painel.</p>
     */
    private List<TelemetriaEvent> converter(Response resposta) {
        List<TelemetriaEvent> eventos = new ArrayList<>();
        if (resposta == null) {
            return eventos;
        }
        for (Response entrada : resposta) {
            try {
                // Cada entrada e [id, [campo, valor, ...]].
                Response campos = entrada.get(1);
                if (campos == null) {
                    continue;
                }
                for (int i = 0; i + 1 < campos.size(); i += 2) {
                    if (CAMPO.equals(campos.get(i).toString())) {
                        eventos.add(objectMapper.readValue(
                                campos.get(i + 1).toString(), TelemetriaEvent.class));
                    }
                }
            } catch (Exception ignorado) {
                // Registro ilegivel nao invalida os demais.
            }
        }
        return eventos;
    }
}
