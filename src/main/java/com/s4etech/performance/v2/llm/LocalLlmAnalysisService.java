package com.s4etech.performance.v2.llm;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.s4etech.performance.v2.AppPaths;
import com.s4etech.performance.v2.model.CameraConfig;
import com.s4etech.performance.v2.model.PipelineTestConfig;
import com.s4etech.performance.v2.model.PipelineTestSummary;
import com.s4etech.performance.v2.tuning.RecommendationSelector;

public class LocalLlmAnalysisService {

    private static final int RANKING_LIMIT = 5;
    private static final int OLLAMA_RETRY_LIMIT = 12;
    private static final long OLLAMA_RETRY_DELAY_MILLIS = 10_000;
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LocalLlmConfig config;
    private final HttpClient httpClient;
    private boolean available = true;
    private String unavailableReason;

    public LocalLlmAnalysisService(LocalLlmConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .build();
    }

    public boolean isEnabled() {
        return config.isEnabled() && available;
    }

    public void prepare() {
        if (!config.isEnabled()) {
            return;
        }

        System.out.println();
        System.out.println("====================================================");
        System.out.println("PRE-CHECK DA LLM LOCAL");
        System.out.println("====================================================");
        System.out.println("Endpoint: " + config.getEndpoint());
        System.out.println("Modelo: " + config.getModel());

        try {
            if (isModelInstalled()) {
                System.out.println("Modelo LLM encontrado localmente.");
                return;
            }

            System.out.println("Modelo LLM nao encontrado localmente: " + config.getModel());

            if (!config.isAutoPullModel()) {
                markUnavailable("Instale com: ollama pull " + config.getModel()
                        + " ou habilite llm.autoPullModel=true.");
                System.out.println(unavailableReason);
                return;
            }

            System.out.println("Baixando modelo automaticamente. Isso pode demorar na primeira execucao.");
            pullModel();

            if (isModelInstalled()) {
                System.out.println("Modelo LLM instalado com sucesso.");
                return;
            }

            markUnavailable("Ollama concluiu o pull, mas o modelo ainda nao apareceu em /api/tags: "
                    + config.getModel());
            System.out.println(unavailableReason);
        } catch (IOException e) {
            markUnavailable("LLM local indisponivel: " + describeException(e)
                    + ". Verifique se o Ollama esta rodando em " + config.getEndpoint() + ".");
            System.out.println(unavailableReason);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markUnavailable("Pre-check da LLM local interrompido.");
            System.out.println(unavailableReason);
        } catch (IllegalArgumentException e) {
            markUnavailable("Configuracao invalida da LLM local: " + e.getMessage());
            System.out.println(unavailableReason);
        }
    }

    public Optional<String> analyze(
            CameraConfig camera,
            List<PipelineTestSummary> summaries,
            PipelineTestSummary recommendation,
            RecommendationSelector selector) {

        if (!config.isEnabled()) {
            return Optional.empty();
        }

        if (!available) {
            return Optional.of(unavailableReason);
        }

        if (recommendation == null) {
            return Optional.of("LLM local nao executada: nao existe recomendacao para analisar.");
        }

        String prompt = buildPrompt(camera, summaries, recommendation, selector);
        String requestBody = buildOllamaRequest(prompt);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getEndpoint()))
                    .timeout(config.getTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.of("LLM local respondeu HTTP " + response.statusCode()
                        + ". Verifique endpoint/modelo em llm.endpoint e llm.model.");
            }

            String analysis = extractJsonString(response.body(), "response");

            if (analysis == null || analysis.isBlank()) {
                return Optional.of("LLM local respondeu sem o campo 'response'. Verifique se o endpoint e compativel com Ollama.");
            }

            return Optional.of(analysis.trim());
        } catch (IOException e) {
            return Optional.of("LLM local indisponivel: " + describeException(e)
                    + ". Verifique se o Ollama esta rodando em " + config.getEndpoint()
                    + " e se o modelo '" + config.getModel() + "' esta instalado.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of("LLM local interrompida durante a analise.");
        } catch (IllegalArgumentException e) {
            return Optional.of("Configuracao invalida da LLM local: " + e.getMessage());
        }
    }

    public Path writeMarkdownReport(CameraConfig camera, String analysis) {
        Path file = getReportFile(camera);

        try {
            Files.createDirectories(file.getParent());

            OpenOption[] options = new OpenOption[] {
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            };

            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, options)) {
                writer.write("# Analise LLM local");
                writer.newLine();
                writer.newLine();
                writer.write("- Data: " + LocalDateTime.now().format(REPORT_TIMESTAMP_FORMATTER));
                writer.newLine();
                writer.write("- Camera: " + (camera != null ? camera.getCode() : "camera"));
                writer.newLine();
                writer.write("- Modelo: " + config.getModel());
                writer.newLine();
                writer.newLine();
                writer.write(analysis == null ? "" : analysis);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erro ao gravar relatorio Markdown da LLM.");
            e.printStackTrace();
        }

        return file;
    }

    private String buildPrompt(
            CameraConfig camera,
            List<PipelineTestSummary> summaries,
            PipelineTestSummary recommendation,
            RecommendationSelector selector) {

        List<PipelineTestSummary> ranked = selector.rankBestFirst(summaries);
        StringBuilder prompt = new StringBuilder();

        prompt.append("Voce e um assistente tecnico de performance de cameras RTSP.\n");
        prompt.append("Importante: voce NAO decide a configuracao final. A decisao ja foi feita por algoritmo deterministico.\n");
        prompt.append("Sua tarefa e explicar a escolha, apontar riscos e sugerir proximos testes objetivos.\n");
        prompt.append("Responda em portugues do Brasil, em no maximo 12 linhas, sem inventar dados.\n\n");

        prompt.append("Camera: ").append(camera != null ? camera.getCode() : "camera").append('\n');
        prompt.append("URL mascarada: ").append(camera != null ? camera.getMaskedRtspUrl() : "").append("\n\n");

        prompt.append("Recomendacao escolhida pelo algoritmo:\n");
        appendSummary(prompt, recommendation, selector);
        prompt.append('\n');

        prompt.append("Top configuracoes por estabilidade:\n");
        appendRanking(prompt, ranked, selector, RANKING_LIMIT);
        prompt.append('\n');

        prompt.append("Piores configuracoes:\n");
        appendRanking(prompt, getWorstResults(ranked), selector, Math.min(3, ranked.size()));
        prompt.append('\n');

        prompt.append("Criterios objetivos do algoritmo:\n");
        prompt.append("- RECOMENDADA se scoreMedio >= 90, scoreMinimo >= 85, picos120 == 0, picos200 == 0, erros == 0, watchdog == 0.\n");
        prompt.append("- RESSALVA se tem picos120, mas nao tem picos200, erro, watchdog ou score abaixo do minimo.\n");
        prompt.append("- REPROVADA se tem picos200, erro, watchdog, scoreMedio < 90 ou scoreMinimo < 85.\n");
        prompt.append("- estabilidade vem antes de score medio.\n");
        prompt.append("- em empate tecnico, preferir menor latency e menor buffer.\n\n");

        prompt.append("Formato da resposta:\n");
        prompt.append("1. Resumo da recomendacao.\n");
        prompt.append("2. Por que ela venceu.\n");
        prompt.append("3. Riscos ou ressalvas.\n");
        prompt.append("4. Proximos testes sugeridos.\n");

        return prompt.toString();
    }

    private void appendRanking(
            StringBuilder prompt,
            List<PipelineTestSummary> ranked,
            RecommendationSelector selector,
            int limit) {

        if (ranked == null || ranked.isEmpty()) {
            prompt.append("- nenhuma configuracao disponivel.\n");
            return;
        }

        int count = Math.min(limit, ranked.size());

        for (int index = 0; index < count; index++) {
            prompt.append(index + 1).append(". ");
            appendSummary(prompt, ranked.get(index), selector);
        }
    }

    private void appendSummary(
            StringBuilder prompt,
            PipelineTestSummary summary,
            RecommendationSelector selector) {

        PipelineTestConfig testConfig = summary.getConfig();

        prompt.append(testConfig.getProtocol())
                .append(" | ")
                .append(testConfig.getAccelerationDescription())
                .append(" | decoder=")
                .append(testConfig.getDecoderElement())
                .append(" | latency=")
                .append(testConfig.getLatencyMs())
                .append(" | buffer=")
                .append(testConfig.getBufferMs())
                .append(" | fpsMedio=")
                .append(format(summary.getAverageFps()))
                .append(" | fpsMinimo=")
                .append(summary.getMinimumFps())
                .append(" | piorMaiorIntervaloMs=")
                .append(summary.getWorstMaximumIntervalMs())
                .append(" | picos80=")
                .append(summary.getTotalPeaksAbove80Ms())
                .append(" | picos120=")
                .append(summary.getTotalPeaksAbove120Ms())
                .append(" | picos200=")
                .append(summary.getTotalPeaksAbove200Ms())
                .append(" | erros=")
                .append(summary.getTotalErrors())
                .append(" | watchdog=")
                .append(summary.getTotalWatchdog())
                .append(" | scoreMedio=")
                .append(format(summary.getAverageScore()))
                .append(" | scoreMinimo=")
                .append(format(summary.getMinimumScore()))
                .append(" | status=")
                .append(selector.getRecommendationStatus(summary))
                .append('\n');
    }

    private List<PipelineTestSummary> getWorstResults(List<PipelineTestSummary> ranked) {
        List<PipelineTestSummary> worst = new java.util.ArrayList<>(ranked);
        java.util.Collections.reverse(worst);
        return worst;
    }

    private String buildOllamaRequest(String prompt) {
        return "{"
                + "\"model\":\"" + escapeJson(config.getModel()) + "\","
                + "\"prompt\":\"" + escapeJson(prompt) + "\","
                + "\"stream\":false,"
                + "\"options\":{\"temperature\":0.2,\"num_predict\":" + config.getMaxTokens() + "}"
                + "}";
    }

    private boolean isModelInstalled() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(getOllamaEndpoint("tags"))
                .timeout(config.getTimeout())
                .GET()
                .build();

        HttpResponse<String> response = sendWithOllamaRetry(request, "listar modelos");

        return extractJsonStringValues(response.body(), "name").contains(config.getModel());
    }

    private void pullModel() throws IOException, InterruptedException {
        String requestBody = "{"
                + "\"name\":\"" + escapeJson(config.getModel()) + "\","
                + "\"stream\":false"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(getOllamaEndpoint("pull"))
                .timeout(config.getTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        sendWithOllamaRetry(request, "baixar o modelo " + config.getModel());
    }

    private HttpResponse<String> sendWithOllamaRetry(HttpRequest request, String operation)
            throws IOException, InterruptedException {

        for (int attempt = 1; attempt <= OLLAMA_RETRY_LIMIT; attempt++) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response;
            }

            if (!isUpgradeInProgress(response)) {
                throw new IOException("Ollama respondeu HTTP " + response.statusCode()
                        + " ao " + operation + ": " + abbreviate(response.body()));
            }

            if (attempt == OLLAMA_RETRY_LIMIT) {
                throw new IOException("Ollama continua em upgrade apos " + OLLAMA_RETRY_LIMIT
                        + " tentativas ao " + operation);
            }

            System.out.println("Ollama esta em upgrade. Tentando novamente em "
                    + (OLLAMA_RETRY_DELAY_MILLIS / 1000) + "s (tentativa "
                    + attempt + "/" + OLLAMA_RETRY_LIMIT + ").");
            Thread.sleep(OLLAMA_RETRY_DELAY_MILLIS);
        }

        throw new IOException("Ollama indisponivel ao " + operation);
    }

    private boolean isUpgradeInProgress(HttpResponse<String> response) {
        return response != null
                && response.body() != null
                && response.body().toLowerCase(java.util.Locale.ROOT).contains("upgrade in progress");
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "sem corpo de resposta";
        }

        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();

        if (normalized.length() <= 160) {
            return normalized;
        }

        return normalized.substring(0, 160) + "...";
    }

    private URI getOllamaEndpoint(String apiName) {
        URI endpoint = URI.create(config.getEndpoint());
        String path = endpoint.getPath();
        String basePath = path == null ? "" : path;
        int apiIndex = basePath.indexOf("/api/");

        if (apiIndex >= 0) {
            basePath = basePath.substring(0, apiIndex);
        }

        String endpointPath = basePath + "/api/" + apiName;

        return URI.create(endpoint.resolve(endpointPath).toString());
    }

    private void markUnavailable(String reason) {
        available = false;
        unavailableReason = reason;
    }

    private String describeException(Exception exception) {
        String message = exception.getMessage();

        if (message != null && !message.isBlank()) {
            return exception.getClass().getSimpleName() + ": " + message;
        }

        Throwable cause = exception.getCause();

        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return exception.getClass().getSimpleName() + " causado por "
                    + cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }

        return exception.getClass().getSimpleName();
    }

    private String extractJsonString(String json, String fieldName) {
        List<String> values = extractJsonStringValues(json, fieldName);

        if (values.isEmpty()) {
            return null;
        }

        return values.get(0);
    }

    private List<String> extractJsonStringValues(String json, String fieldName) {
        List<String> values = new ArrayList<>();

        if (json == null || fieldName == null) {
            return values;
        }

        String marker = "\"" + fieldName + "\"";
        int searchIndex = 0;

        while (searchIndex < json.length()) {
            int markerIndex = json.indexOf(marker, searchIndex);

            if (markerIndex < 0) {
                return values;
            }

            int colonIndex = json.indexOf(':', markerIndex + marker.length());

            if (colonIndex < 0) {
                return values;
            }

            int startQuoteIndex = json.indexOf('"', colonIndex + 1);

            if (startQuoteIndex < 0) {
                return values;
            }

            StringBuilder value = new StringBuilder();
            boolean escaping = false;
            boolean foundValue = false;

            for (int index = startQuoteIndex + 1; index < json.length(); index++) {
                char current = json.charAt(index);

                if (escaping) {
                    appendEscapedCharacter(value, current, json, index);

                    if (current == 'u') {
                        index += 4;
                    }

                    escaping = false;
                    continue;
                }

                if (current == '\\') {
                    escaping = true;
                    continue;
                }

                if (current == '"') {
                    values.add(value.toString());
                    searchIndex = index + 1;
                    foundValue = true;
                    break;
                }

                value.append(current);
            }

            if (!foundValue) {
                return values;
            }
        }

        return values;
    }

    private void appendEscapedCharacter(StringBuilder value, char current, String json, int index) {
        switch (current) {
            case '"':
                value.append('"');
                break;
            case '\\':
                value.append('\\');
                break;
            case '/':
                value.append('/');
                break;
            case 'b':
                value.append('\b');
                break;
            case 'f':
                value.append('\f');
                break;
            case 'n':
                value.append('\n');
                break;
            case 'r':
                value.append('\r');
                break;
            case 't':
                value.append('\t');
                break;
            case 'u':
                appendUnicodeCharacter(value, json, index);
                break;
            default:
                value.append(current);
                break;
        }
    }

    private void appendUnicodeCharacter(StringBuilder value, String json, int index) {
        if (index + 4 >= json.length()) {
            return;
        }

        String hex = json.substring(index + 1, index + 5);

        try {
            value.append((char) Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            value.append("\\u").append(hex);
        }
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder escaped = new StringBuilder();

        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);

            switch (current) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    escaped.append(current);
                    break;
            }
        }

        return escaped.toString();
    }

    private Path getReportFile(CameraConfig camera) {
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMATTER);
        String cameraCode = cleanFileName(camera != null ? camera.getCode() : "camera");

        return AppPaths.getLogFile(
                cameraCode,
                "llm-analysis-" + cameraCode + "-" + timestamp + ".md"
        );
    }

    private String cleanFileName(String value) {
        if (value == null || value.isBlank()) {
            return "camera";
        }

        return value.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private String format(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
