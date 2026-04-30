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
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.s4etech.performance.v2.model.CameraConfig;
import com.s4etech.performance.v2.model.PipelineTestConfig;
import com.s4etech.performance.v2.model.PipelineTestSummary;
import com.s4etech.performance.v2.tuning.RecommendationSelector;

public class LocalLlmAnalysisService {

    private static final int RANKING_LIMIT = 5;
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LocalLlmConfig config;
    private final HttpClient httpClient;

    public LocalLlmAnalysisService(LocalLlmConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .build();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public Optional<String> analyze(
            CameraConfig camera,
            List<PipelineTestSummary> summaries,
            PipelineTestSummary recommendation,
            RecommendationSelector selector) {

        if (!config.isEnabled()) {
            return Optional.empty();
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
        prompt.append("- recomendada se scoreMedio >= 90, scoreMinimo >= 85, picos200 == 0, erros == 0, watchdog == 0.\n");
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
                .append(selector.isRecommended(summary) ? "RECOMENDADA" : "RESSALVA")
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
        if (json == null || fieldName == null) {
            return null;
        }

        String marker = "\"" + fieldName + "\"";
        int markerIndex = json.indexOf(marker);

        if (markerIndex < 0) {
            return null;
        }

        int colonIndex = json.indexOf(':', markerIndex + marker.length());

        if (colonIndex < 0) {
            return null;
        }

        int startQuoteIndex = json.indexOf('"', colonIndex + 1);

        if (startQuoteIndex < 0) {
            return null;
        }

        StringBuilder value = new StringBuilder();
        boolean escaping = false;

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
                return value.toString();
            }

            value.append(current);
        }

        return null;
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

        return Paths.get(
                System.getProperty("user.dir"),
                "logs",
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
