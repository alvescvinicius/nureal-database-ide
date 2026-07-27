package com.nureal.ide.modulos.atualizacao.infraestrutura;
import com.nureal.ide.modulos.atualizacao.dominio.entidades.GithubRelease;
import com.nureal.ide.modulos.atualizacao.dominio.contratos.RepositorioDeReleasesPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.nureal.ide.core.json.JsonParser;

/**
 * Consulta o ultimo release publicado no GitHub do proprio projeto (endpoint
 * publico "releases/latest" da API REST — nao exige autenticacao para repos
 * publicos, mas o GitHub aplica rate-limit por IP em requisicoes anonimas;
 * suficiente para uma checagem esporadica no startup, nao adequado pra
 * chamar em loop).
 *
 * Fonte unica do "owner/repo" e {@link #REPO} — ver
 * {@code .git/config} (remote origin), e o mesmo repo usado pelo workflow de
 * release (.github/workflows/release.yml) que publica os instaladores.
 *
 * Parsing via {@link JsonParser} (o parser JSON minimo que ja existe em
 * {@code core.json}, criado originalmente para o EXPLAIN FORMAT=JSON — ver
 * {@code ExplainDialog}) em vez de trazer uma biblioteca externa (Jackson/
 * Gson/org.json): mesma filosofia do projeto ja documentada la ("sem
 * dependencia nova so para um unico consumidor").
 *
 * Sem estado, sem instancia — todo metodo roda a chamada de rede
 * SINCRONAMENTE (bloqueia a thread chamadora); quem usa esta classe (ver
 * {@code MainWindow}) e responsavel por rodar numa thread de fundo, nunca na
 * EDT.
 */
public final class UpdateChecker implements RepositorioDeReleasesPort {

    private static final String REPO = "alvescvinicius/nureal-database-ide";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * Busca o release mais recente publicado no GitHub. Lanca {@link IOException}
     * em qualquer falha (rede indisponivel, timeout, resposta que nao seja 200,
     * JSON invalido/inesperado) — o chamador decide como tratar (checagem
     * automatica do startup ignora silenciosamente, ver MainWindow; checagem
     * manual mostra a mensagem ao usuario).
     */
    @Override
    public GithubRelease fetchLatestRelease() throws IOException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                // A API do GitHub recusa (403) requisicoes sem User-Agent.
                .header("User-Agent", "nureal-database-ide-updater")
                .header("Accept", "application/vnd.github+json")
                .timeout(TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Checagem de atualizacao interrompida.", ex);
        }
        if (response.statusCode() != 200) {
            throw new IOException("GitHub respondeu " + response.statusCode()
                    + " ao consultar o ultimo release (" + REPO + ").");
        }
        return parse(response.body());
    }

    /** {@code true} quando {@code release} representa uma versao mais nova que {@link AppVersion#current()}. */
    public static boolean isUpdateAvailable(GithubRelease release) {
        return release != null && SemVer.isNewer(release.tagName(), AppVersion.current());
    }

    /**
     * Converte o JSON cru da resposta em {@link GithubRelease}. {@link JsonParser#parse}
     * devolve tipos genericos ({@code Map<String,Object>} para objeto, {@code List<Object>}
     * para array, {@code Double} para numero) — este metodo faz o "casting"
     * defensivo campo a campo, tratando qualquer formato inesperado como erro
     * (nunca deixa passar um release com dados incompletos silenciosamente).
     */
    @SuppressWarnings("unchecked")
    private static GithubRelease parse(String json) throws IOException {
        Object root;
        try {
            root = JsonParser.parse(json);
        } catch (JsonParser.JsonParseException ex) {
            throw new IOException("Resposta do GitHub em formato inesperado.", ex);
        }
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new IOException("Resposta do GitHub nao e um objeto JSON.");
        }
        Map<String, Object> map = (Map<String, Object>) rootMap;

        String tagName = stringField(map, "tag_name", "");
        if (tagName.isBlank()) {
            throw new IOException("Resposta do GitHub sem 'tag_name'.");
        }
        String name = stringField(map, "name", tagName);
        String htmlUrl = stringField(map, "html_url", "https://github.com/" + REPO + "/releases");
        String body = stringField(map, "body", "");

        return new GithubRelease(tagName, name, htmlUrl, body);
    }

    private static String stringField(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return (v instanceof String s) ? s : fallback;
    }
}
