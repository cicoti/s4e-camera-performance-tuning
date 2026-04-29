package com.s4etech.performance.v2.report;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.s4etech.performance.v2.model.CameraConfig;
import com.s4etech.performance.v2.model.PipelineTestConfig;
import com.s4etech.performance.v2.model.PipelineTestMetrics;
import com.s4etech.performance.v2.tuning.RecommendationSelector;

public class TuningReportService {

    private static final int RANKING_LIMIT = 5;
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void printConsoleReport(
            CameraConfig camera,
            List<PipelineTestMetrics> results,
            PipelineTestMetrics recommendation,
            RecommendationSelector selector) {

        List<PipelineTestMetrics> ranked = selector.rankBestFirst(results);

        System.out.println();
        System.out.println("====================================================");
        System.out.println("Ranking top " + Math.min(RANKING_LIMIT, ranked.size()));
        System.out.println("====================================================");
        printRanking(ranked, selector, RANKING_LIMIT);

        System.out.println();
        System.out.println("====================================================");
        System.out.println("Piores configuracoes");
        System.out.println("====================================================");
        printRanking(getWorstResults(ranked), selector, RANKING_LIMIT);

        System.out.println();
        System.out.println("====================================================");
        System.out.println("Recomendacao final");
        System.out.println("====================================================");

        if (recommendation == null) {
            System.out.println("Nenhuma configuracao candidata foi avaliada.");
            return;
        }

        System.out.println(recommendation.getConfig().getShortDescription());
        System.out.println(recommendation);
        System.out.println("Status recomendacao: "
                + (selector.isRecommended(recommendation) ? "RECOMENDADA" : "RECOMENDADA_COM_RESSALVA"));
        System.out.println("Motivo: " + buildRecommendationReason(recommendation, selector));
        System.out.println("Camera: " + camera.getCode());
        System.out.println("URL: " + camera.getMaskedRtspUrl());
    }

    public Path writeCsvReport(
            CameraConfig camera,
            List<PipelineTestMetrics> results,
            PipelineTestMetrics recommendation,
            RecommendationSelector selector) {

        Path file = getReportFile(camera);

        try {
            Files.createDirectories(file.getParent());

            OpenOption[] options = new OpenOption[] {
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            };

            List<PipelineTestMetrics> ranked = selector.rankBestFirst(results);

            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, options)) {
                writer.write(getCsvHeader());
                writer.newLine();

                int rank = 1;

                for (PipelineTestMetrics metrics : ranked) {
                    writer.write(toCsvLine(camera, metrics, recommendation, selector, rank));
                    writer.newLine();
                    rank++;
                }
            }

            return file;

        } catch (IOException e) {
            System.err.println("Erro ao gravar relatorio CSV de tuning.");
            e.printStackTrace();
            return file;
        }
    }

    private void printRanking(
            List<PipelineTestMetrics> ranked,
            RecommendationSelector selector,
            int limit) {

        if (ranked == null || ranked.isEmpty()) {
            System.out.println("Nenhuma configuracao para exibir.");
            return;
        }

        int count = Math.min(limit, ranked.size());

        for (int index = 0; index < count; index++) {
            PipelineTestMetrics metrics = ranked.get(index);
            PipelineTestConfig config = metrics.getConfig();

            System.out.println((index + 1) + ". "
                    + config.getProtocol()
                    + " | " + config.getAccelerationDescription()
                    + " | latency=" + config.getLatencyMs()
                    + " | buffer=" + config.getBufferMs()
                    + " | score=" + format(metrics.getScore())
                    + " | maiorIntervaloMs=" + metrics.getMaximumIntervalMs()
                    + " | picos120=" + metrics.getPeaksAbove120Ms()
                    + " | picos200=" + metrics.getPeaksAbove200Ms()
                    + " | status=" + (selector.isRecommended(metrics) ? "RECOMENDADA" : "RESSALVA"));
        }
    }

    private List<PipelineTestMetrics> getWorstResults(List<PipelineTestMetrics> ranked) {
        List<PipelineTestMetrics> worst = new ArrayList<>(ranked);
        Collections.reverse(worst);
        return worst;
    }

    private String buildRecommendationReason(
            PipelineTestMetrics recommendation,
            RecommendationSelector selector) {

        PipelineTestConfig config = recommendation.getConfig();

        if (!selector.isRecommended(recommendation)) {
            return "melhor candidata disponivel, mas ficou abaixo de algum criterio minimo objetivo.";
        }

        return "sem erros/watchdog, sem picos acima de 200 ms, score "
                + format(recommendation.getScore())
                + ", maior intervalo "
                + recommendation.getMaximumIntervalMs()
                + " ms, priorizando estabilidade e menor latencia/buffer em empate tecnico.";
    }

    private Path getReportFile(CameraConfig camera) {
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMATTER);
        String cameraCode = cleanFileName(camera != null ? camera.getCode() : "camera");

        return Paths.get(
                System.getProperty("user.dir"),
                "logs",
                "tuning-" + cameraCode + "-" + timestamp + ".csv"
        );
    }

    private String getCsvHeader() {
        return "dataHora;rank;recomendacaoFinal;camera;url;protocolo;codec;aceleracao;decoder;"
                + "latencyMs;bufferMs;watchdogTimeoutMs;outputWidth;outputHeight;durationSeconds;"
                + "status;fpsMedio;fpsMinimo;fpsMaximo;intervaloMedioMs;maiorIntervaloMs;"
                + "picos80;picos120;picos200;erros;watchdog;tempoAtePrimeiroFrameMs;score;"
                + "statusRecomendacao;mensagemErro";
    }

    private String toCsvLine(
            CameraConfig camera,
            PipelineTestMetrics metrics,
            PipelineTestMetrics recommendation,
            RecommendationSelector selector,
            int rank) {

        PipelineTestConfig config = metrics.getConfig();

        return csv(LocalDateTime.now().format(REPORT_TIMESTAMP_FORMATTER)) + ";"
                + rank + ";"
                + (metrics == recommendation) + ";"
                + csv(camera.getCode()) + ";"
                + csv(camera.getMaskedRtspUrl()) + ";"
                + csv(String.valueOf(config.getProtocol())) + ";"
                + csv(String.valueOf(config.getVideoCodec())) + ";"
                + csv(config.getAccelerationDescription()) + ";"
                + csv(config.getDecoderElement()) + ";"
                + config.getLatencyMs() + ";"
                + config.getBufferMs() + ";"
                + config.getWatchdogTimeoutMs() + ";"
                + config.getOutputWidth() + ";"
                + config.getOutputHeight() + ";"
                + config.getTestDurationSeconds() + ";"
                + csv(metrics.getStatus()) + ";"
                + format(metrics.getAverageFps()) + ";"
                + metrics.getMinimumFps() + ";"
                + metrics.getMaximumFps() + ";"
                + format(metrics.getAverageIntervalMs()) + ";"
                + metrics.getMaximumIntervalMs() + ";"
                + metrics.getPeaksAbove80Ms() + ";"
                + metrics.getPeaksAbove120Ms() + ";"
                + metrics.getPeaksAbove200Ms() + ";"
                + metrics.getErrorCount() + ";"
                + metrics.getWatchdogCount() + ";"
                + metrics.getTimeToFirstFrameMs() + ";"
                + format(metrics.getScore()) + ";"
                + csv(selector.isRecommended(metrics) ? "RECOMENDADA" : "RESSALVA") + ";"
                + csv(metrics.getErrorMessage());
    }

    private String cleanFileName(String value) {
        if (value == null || value.isBlank()) {
            return "camera";
        }

        return value.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String format(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }
}
