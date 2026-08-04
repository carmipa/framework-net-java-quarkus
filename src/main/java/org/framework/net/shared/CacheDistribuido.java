package org.framework.net.shared;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Segundo nível de cache para respostas de APIs externas, sobrevivendo a deploys.
 *
 * <p><b>Propósito de negócio:</b> os caches de DNS, GeoIP, CEP e geocodificação
 * vivem em memória e morrem a cada reinício do container. Depois de todo deploy
 * a aplicação volta a bater nas APIs externas do zero — e elas têm limite:
 * {@code ip-api.com} corta em 45 requisições por minuto (foi o que deixou
 * {@code GeoLookupServiceTest} intermitente) e o Nominatim pede ~1 req/s por
 * política de uso. Este cache guarda as respostas fora do processo, de modo que
 * um deploy não jogue fora o que já foi consultado.</p>
 *
 * <p><b>Invariantes do domínio:</b> é <b>L2</b>, nunca L1 — o mapa em memória de
 * cada serviço continua sendo consultado primeiro, então o caminho quente não
 * ganha ida à rede. Toda chave é prefixada por {@code fnet:} mais o namespace do
 * serviço, para que nada colida com outro consumidor do mesmo Redis. Todo valor
 * grava com TTL: cache sem expiração vira fonte de dado velho.</p>
 *
 * <p><b>Comportamento em caso de falha:</b> <b>degrada em silêncio e por
 * projeto</b>. Redis desligado, inalcançável ou lento faz {@link #obter} devolver
 * {@link Optional#empty()} e {@link #guardar} não fazer nada — o serviço chamador
 * segue para a origem exatamente como fazia antes de existir cache distribuído.
 * A primeira falha é registrada em WARN uma única vez, para não inundar o log.
 * Nenhuma exceção escapa deste componente.</p>
 */
@ApplicationScoped
public class CacheDistribuido {

    private static final Logger LOG = Logger.getLogger(CacheDistribuido.class);
    private static final String PREFIXO = "fnet:";

    /**
     * Injeção preguiçosa de propósito.
     *
     * <p>Sem {@code quarkus.redis.hosts} configurado o bean do Redis fica
     * <i>inativo</i>, e injetá-lo diretamente derruba a inicialização com
     * {@code InactiveBeanException} — foi o que quebrou toda a suíte de testes,
     * onde não há Redis nem Docker. Com {@link Instance} a resolução só acontece
     * quando o cache está explicitamente ligado.</p>
     */
    @Inject
    jakarta.enterprise.inject.Instance<RedisDataSource> redisDataSource;

    @ConfigProperty(name = "framework.cache.distribuido-enabled", defaultValue = "false")
    boolean habilitado;

    private ValueCommands<String, String> valores;
    private final AtomicBoolean falhaJaRegistrada = new AtomicBoolean(false);

    @PostConstruct
    void iniciar() {
        if (!habilitado) {
            return;
        }
        try {
            if (!redisDataSource.isResolvable()) {
                LOG.warn("Cache distribuido ligado mas o cliente Redis nao esta disponivel. "
                        + "Seguindo so com cache em memoria.");
                return;
            }
            valores = redisDataSource.get().value(String.class);
            LOG.info("Cache distribuido ativo (Redis) para respostas de APIs externas.");
        } catch (RuntimeException ex) {
            valores = null;
            LOG.warnf("Cache distribuido indisponivel no start (%s). Seguindo so com cache em memoria.",
                    ex.getClass().getSimpleName());
        }
    }

    /**
     * Lê um valor do L2.
     *
     * <p><b>Comportamento em caso de falha:</b> devolve {@link Optional#empty()}
     * — indistinguível de "não estava no cache", que é exatamente o que o
     * chamador precisa saber para ir à origem.</p>
     */
    public Optional<String> obter(String namespace, String chave) {
        if (valores == null || chave == null || chave.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(valores.get(montarChave(namespace, chave)));
        } catch (RuntimeException ex) {
            registrarFalhaUmaVez(ex);
            return Optional.empty();
        }
    }

    /**
     * Grava um valor no L2 com expiração.
     *
     * <p><b>Invariantes do domínio:</b> TTL nulo ou não positivo faz a gravação
     * ser ignorada — entrada eterna em cache de dado externo é defeito, não
     * otimização.</p>
     */
    public void guardar(String namespace, String chave, String valor, Duration ttl) {
        if (valores == null || chave == null || chave.isBlank() || valor == null) {
            return;
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            valores.setex(montarChave(namespace, chave), ttl.toSeconds(), valor);
        } catch (RuntimeException ex) {
            registrarFalhaUmaVez(ex);
        }
    }

    /** {@code true} quando o L2 está configurado e ainda não falhou no start. */
    public boolean ativo() {
        return valores != null;
    }

    private String montarChave(String namespace, String chave) {
        return PREFIXO + namespace + ":" + chave;
    }

    private void registrarFalhaUmaVez(RuntimeException ex) {
        if (falhaJaRegistrada.compareAndSet(false, true)) {
            LOG.warnf("Cache distribuido falhou (%s: %s). A aplicacao segue com cache em memoria; "
                            + "este aviso nao se repete.",
                    ex.getClass().getSimpleName(), ex.getMessage());
        }
    }
}
