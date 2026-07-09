package org.framework.net.localizacao.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geocodificação (endereço → coordenadas) via
 * <a href="https://nominatim.org/release-docs/latest/api/Search/">Nominatim/OpenStreetMap</a>.
 *
 * <p>A política de uso do Nominatim exige um {@code User-Agent} identificável e no máximo
 * ~1 req/s; por isso os resultados são cacheados agressivamente (endereços são estáticos).
 */
@ApplicationScoped
public class NominatimGeocoder {

    private static final Logger LOG = Logger.getLogger(NominatimGeocoder.class);
    private static final int MAX_CACHE = 500;

    @ConfigProperty(name = "framework.localizacao.nominatim-url",
            defaultValue = "https://nominatim.openstreetmap.org/search")
    String nominatimUrl;

    @ConfigProperty(name = "framework.localizacao.user-agent", defaultValue = "FrameworkNetRedes/1.0")
    String userAgent;

    @ConfigProperty(name = "framework.localizacao.http-timeout-seconds", defaultValue = "5")
    int timeoutSeconds;

    @Inject
    ObjectMapper objectMapper;

    private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
    private volatile HttpClient httpClient;

    /** Geocodifica a consulta (livre) restrita ao Brasil; {@code Optional.empty()} se nada for encontrado. */
    public Optional<Map<String, Object>> geocodificar(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String chave = query.strip().toLowerCase();
        Map<String, Object> cached = cache.get(chave);
        if (cached != null) {
            return Optional.of(new LinkedHashMap<>(cached));
        }
        try {
            String uri = nominatimUrl + "?format=jsonv2&limit=1&addressdetails=0&countrycodes=br&q="
                    + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("Accept", "application/json")
                    .header("Accept-Language", "pt-BR")
                    .header("User-Agent", userAgent)
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .GET()
                    .build();
            HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            JsonNode arr = objectMapper.readTree(response.body());
            if (!arr.isArray() || arr.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = arr.get(0);
            String lat = first.path("lat").asText("");
            String lon = first.path("lon").asText("");
            if (lat.isBlank() || lon.isBlank()) {
                return Optional.empty();
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("lat", parseDouble(lat));
            out.put("lon", parseDouble(lon));
            out.put("display_name", first.path("display_name").asText(""));
            out.put("fonte", "OpenStreetMap/Nominatim");
            guardarCache(chave, out);
            return Optional.of(new LinkedHashMap<>(out));
        } catch (Exception ex) {
            LOG.warnf("Falha no geocoding Nominatim para \"%s\": %s", query, ex.getMessage());
            return Optional.empty();
        }
    }

    private static Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void guardarCache(String chave, Map<String, Object> value) {
        if (cache.size() >= MAX_CACHE) {
            cache.clear();
        }
        cache.put(chave, new LinkedHashMap<>(value));
    }

    private HttpClient client() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                            .build();
                }
            }
        }
        return httpClient;
    }
}
