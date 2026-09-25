package org.framework.net.localizacao.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.framework.net.shared.CacheDistribuido;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F29: a política do Nominatim é ~1 req/s no TOTAL (é o IP da VPS inteira que pode ser banido) e o
 * reverse (GPS) não tinha cache nem freio. Servidor local falso conta as chamadas — nenhum tráfego sai
 * para a internet neste teste.
 */
class NominatimGeocoderTest {

    private HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private NominatimGeocoder geo;

    @BeforeEach
    void subir() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", troca -> {
            chamadas.incrementAndGet();
            byte[] corpo = ("{\"display_name\":\"Rua X, Guarulhos\",\"address\":{\"road\":\"Rua X\",\"city\":\"Guarulhos\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json");
            troca.sendResponseHeaders(200, corpo.length);
            troca.getResponseBody().write(corpo);
            troca.close();
        });
        servidor.start();
        geo = new NominatimGeocoder();
        geo.nominatimUrl = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/search";
        geo.userAgent = "FrameworkNetRedes/teste";
        geo.timeoutSeconds = 3;
        geo.cacheTtlSeconds = 60;
        geo.objectMapper = new ObjectMapper();
        geo.cacheDistribuido = new CacheDistribuido();
    }

    @AfterEach
    void descer() {
        servidor.stop(0);
    }

    @Test
    void reverseRepetidoUsaCache() {
        assertTrue(geo.reverse(-23.4543, -46.5333).isPresent());
        assertTrue(geo.reverse(-23.4543, -46.5333).isPresent());
        assertEquals(1, chamadas.get(), "a mesma coordenada não pode ir duas vezes ao Nominatim");
    }

    @Test
    void freioGlobalDeUmaPorSegundo() throws Exception {
        assertTrue(geo.reverse(-23.1, -46.1).isPresent());
        geo.reverse(-22.2, -45.2);                  // outra coordenada, no mesmo segundo
        assertEquals(1, chamadas.get(), "duas chamadas externas no mesmo segundo violam a política");
        Thread.sleep(1_100);
        assertTrue(geo.reverse(-21.3, -44.3).isPresent());
        assertEquals(2, chamadas.get(), "controle positivo: passado 1 s, a chamada volta a sair");
    }

    @Test
    void coordenadaInvalidaNaoSaiParaFora() {
        assertTrue(geo.reverse(Double.NaN, 0).isEmpty());
        assertTrue(geo.reverse(91, 0).isEmpty());
        assertTrue(geo.reverse(0, 181).isEmpty());
        assertEquals(0, chamadas.get());
    }
}
