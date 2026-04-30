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
import com.s4etech.performance.v2.model.PipelineTestSummary;
import com.s4etech.performance.v2.tuning.RecommendationSelector;

public class TuningReportService {

    private static final int RANKING_LIMIT = 5;
    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter REPORT_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void printConsoleReport(
            CameraConfig camera,
            List<PipelineTestSummary> summaries,
            PipelineTestSummary recommendation,
            RecommendationSelector selector) {

        List<PipelineTestSummary> ranked = selector.rankBestFirst(summaries);

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
            List<PipelineTestSummary> summaries,
            PipelineTestSummary recommendation,
            RecommendationSelector selector) {

        Path file = getReportFile(camera);

        try {
            Files.createDirectories(file.getParent());

            OpenOption[] options = new OpenOption[] {
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            };

            List<PipelineTestSummary> ranked = selector.rankBestFirst(summaries);

            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, options)) {
                writer.write(getCsvHeader());
                writer.newLine();

                int rank = 1;

                for (PipelineTestSummary summary : ranked) {
                    writer.write(toCsvLine(camera, summary, recommendation, selector, rank));
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
            List<PipelineTestSummary> ranked,
            RecommendationSelector selector,
            int limit) {

        if (ranked == null || ranked.isEmpty()) {
            System.out.println("Nenhuma configuracao para exibir.");
            return;
        }

        int count = Math.min(limit, ranked.size());

        for (int index = 0; index < count; index++) {
            PipelineTestSummary summary = ranked.get(index);
            PipelineTestConfig config = summary.getConfig();

            System.out.println((index + 1) + ". "
                    + config.getProtocol()
                    + " | " + config.getAccelerationDescription()
                    + " | latency=" + config.getLatencyMs()
                    + " | buffer=" + config.getBufferMs()
                    + " | scoreMedio=" + format(summary.getAverageScore())
                    + " | scoreMinimo=" + format(summary.getMinimumScore())
                    + " | piorMaiorIntervaloMs=" + summary.getWorstMaximumIntervalMs()
                    + " | picos120=" + summary.getTotalPeaksAbove120Ms()
                    + " | picos200=" + summary.getTotalPeaksAbove200Ms()
                    + " | repeticoes=" + summary.getExecutionCount()
                    + " | status=" + (selector.isRecommended(summary) ? "RECOMENDADA" : "RESSALVA"));
        }
    }

    private List<PipelineTestSummary> getWorstResults(List<PipelineTestSummary> ranked) {
        List<PipelineTestSummary> worst = new ArrayList<>(ranked);
        Collections.reverse(worst);
        return worst;
    }

    private String buildRecommendationReason(
            PipelineTestSummary recommendation,
            RecommendationSelector selector) {

        if (!selector.isRecommended(recommendation)) {
            return "melhor candidata disponivel, mas ficou abaixo de algum criterio minimo objetivo.";
        }

        return "sem erros/watchdog, sem picos acima de 200 ms, score medio "
                + format(recommendation.getAverageScore())
                + ", score minimo "
                + format(recommendation.getMinimumScore())
                + ", pior maior intervalo "
                + recommendation.getWorstMaximumIntervalMs()
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
                + "latencyMs;bufferMs;watchdogTimeoutMs;outputWidth;outputHeight;durationSeconds;repeticoes;"
                + "status;fpsMedio;fpsMinimo;fpsMaximo;intervaloMedioMs;piorMaiorIntervaloMs;"
                + "picos80Total;picos120Total;picos200Total;errosTotal;watchdogTotal;"
                + "tempoMedioAtePrimeiroFrameMs;scoreMedio;scoreMinimo;statusRecomendacao;mensagemErro";
    }

    private String toCsvLine(
            CameraConfig camera,
            PipelineTestSummary summary,
            PipelineTestSummary recommendation,
            RecommendationSelector selector,
            int rank) {

        PipelineTestConfig config = summary.getConfig();

        return csv(LocalDateTime.now().format(REPORT_TIMESTAMP_FORMATTER)) + ";"
                + rank + ";"
                + (summary == recommendation) + ";"
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
                + summary.getExecutionCount() + ";"
                + csv(summary.getStatus()) + ";"
                + format(summary.getAverageFps()) + ";"
                + summary.getMinimumFps() + ";"
                + summary.getMaximumFps() + ";"
                + format(summary.getAverageIntervalMs()) + ";"
                + summary.getWorstMaximumIntervalMs() + ";"
                + summary.getTotalPeaksAbove80Ms() + ";"
                + summary.getTotalPeaksAbove120Ms() + ";"
                + summary.getTotalPeaksAbove200Ms() + ";"
                + summary.getTotalErrors() + ";"
                + summary.getTotalWatchdog() + ";"
                + format(summary.getAverageTimeToFirstFrameMs()) + ";"
                + format(summary.getAverageScore()) + ";"
                + format(summary.getMinimumScore()) + ";"
                + csv(selector.isRecommended(summary) ? "RECOMENDADA" : "RESSALVA") + ";"
                + csv(summary.getErrorMessage());
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
