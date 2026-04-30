package com.s4etech.performance.v2.report;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.s4etech.performance.v2.AppPaths;
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
        System.out.println("Status recomendacao: " + selector.getRecommendationStatus(recommendation));
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

    public Path writeRecommendationTextReport(
            CameraConfig camera,
            List<PipelineTestSummary> summaries,
            PipelineTestSummary recommendation,
            RecommendationSelector selector,
            Optional<String> llmAnalysis) {

        Path file = getRecommendationReportFile(camera);

        try {
            Files.createDirectories(file.getParent());

            OpenOption[] options = new OpenOption[] {
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            };

            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, options)) {
                writer.write("RELATORIO DE RECOMENDACAO");
                writer.newLine();
                writer.write("=========================");
                writer.newLine();
                writer.newLine();
                writer.write("Data: " + LocalDateTime.now().format(REPORT_TIMESTAMP_FORMATTER));
                writer.newLine();
                writer.write("Camera: " + (camera != null ? camera.getCode() : "camera"));
                writer.newLine();
                writer.write("URL: " + (camera != null ? camera.getMaskedRtspUrl() : ""));
                writer.newLine();
                writer.newLine();

                writeAlgorithmRecommendation(writer, recommendation, selector);
                writer.newLine();
                writeTopRanking(writer, summaries, selector);
                writer.newLine();
                writeLlmRecommendation(writer, llmAnalysis);
            }

            return file;
        } catch (IOException e) {
            System.err.println("Erro ao gravar relatorio TXT de recomendacao.");
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
                    + " | status=" + selector.getRecommendationStatus(summary));
        }
    }

    private List<PipelineTestSummary> getWorstResults(List<PipelineTestSummary> ranked) {
        List<PipelineTestSummary> worst = new ArrayList<>(ranked);
        Collections.reverse(worst);
        return worst;
    }

    private void writeAlgorithmRecommendation(
            BufferedWriter writer,
            PipelineTestSummary recommendation,
            RecommendationSelector selector) throws IOException {

        writer.write("RECOMENDACAO DO PROGRAMA");
        writer.newLine();
        writer.write("------------------------");
        writer.newLine();

        if (recommendation == null) {
            writer.write("Nenhuma configuracao candidata foi avaliada.");
            writer.newLine();
            return;
        }

        PipelineTestConfig config = recommendation.getConfig();

        writer.write("Status: " + selector.getRecommendationStatus(recommendation));
        writer.newLine();
        writer.write("Configuracao: " + config.getShortDescription());
        writer.newLine();
        writer.write("Metricas: fpsMedio=" + format(recommendation.getAverageFps())
                + ", fpsMinimo=" + recommendation.getMinimumFps()
                + ", piorMaiorIntervaloMs=" + recommendation.getWorstMaximumIntervalMs()
                + ", picos80=" + recommendation.getTotalPeaksAbove80Ms()
                + ", picos120=" + recommendation.getTotalPeaksAbove120Ms()
                + ", picos200=" + recommendation.getTotalPeaksAbove200Ms()
                + ", erros=" + recommendation.getTotalErrors()
                + ", watchdog=" + recommendation.getTotalWatchdog()
                + ", scoreMedio=" + format(recommendation.getAverageScore())
                + ", scoreMinimo=" + format(recommendation.getMinimumScore())
                + ", repeticoes=" + recommendation.getExecutionCount());
        writer.newLine();
        writer.write("Motivo: " + buildRecommendationReason(recommendation, selector));
        writer.newLine();
    }

    private void writeTopRanking(
            BufferedWriter writer,
            List<PipelineTestSummary> summaries,
            RecommendationSelector selector) throws IOException {

        writer.write("TOP CONFIGURACOES");
        writer.newLine();
        writer.write("-----------------");
        writer.newLine();

        List<PipelineTestSummary> ranked = selector.rankBestFirst(summaries);

        if (ranked.isEmpty()) {
            writer.write("Nenhuma configuracao para exibir.");
            writer.newLine();
            return;
        }

        int count = Math.min(RANKING_LIMIT, ranked.size());

        for (int index = 0; index < count; index++) {
            PipelineTestSummary summary = ranked.get(index);
            PipelineTestConfig config = summary.getConfig();

            writer.write((index + 1) + ". "
                    + config.getProtocol()
                    + " | " + config.getAccelerationDescription()
                    + " | latency=" + config.getLatencyMs()
                    + " | buffer=" + config.getBufferMs()
                    + " | scoreMedio=" + format(summary.getAverageScore())
                    + " | scoreMinimo=" + format(summary.getMinimumScore())
                    + " | piorMaiorIntervaloMs=" + summary.getWorstMaximumIntervalMs()
                    + " | picos120=" + summary.getTotalPeaksAbove120Ms()
                    + " | picos200=" + summary.getTotalPeaksAbove200Ms()
                    + " | status=" + selector.getRecommendationStatus(summary));
            writer.newLine();
        }
    }

    private void writeLlmRecommendation(
            BufferedWriter writer,
            Optional<String> llmAnalysis) throws IOException {

        writer.write("ANALISE DA LLM LOCAL");
        writer.newLine();
        writer.write("--------------------");
        writer.newLine();

        if (llmAnalysis == null || llmAnalysis.isEmpty() || llmAnalysis.get().isBlank()) {
            writer.write("LLM local sem analise para esta camera.");
            writer.newLine();
            return;
        }

        writer.write(llmAnalysis.get());
        writer.newLine();
    }

    private String buildRecommendationReason(
            PipelineTestSummary recommendation,
            RecommendationSelector selector) {

        if (!selector.isRecommended(recommendation)) {
            if (recommendation.getTotalPeaksAbove200Ms() > 0
                    || recommendation.getTotalErrors() > 0
                    || recommendation.getTotalWatchdog() > 0
                    || recommendation.getAverageScore() < 90
                    || recommendation.getMinimumScore() < 85) {
                return "melhor candidata disponivel, mas reprovada em algum criterio minimo objetivo.";
            }

            return "melhor candidata disponivel, mas com ressalva por picos acima de 120 ms.";
        }

        return "sem erros/watchdog, sem picos acima de 120 ms ou 200 ms, score medio "
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

        return AppPaths.getLogFile(
                cameraCode,
                "tuning-" + cameraCode + "-" + timestamp + ".csv"
        );
    }

    private Path getRecommendationReportFile(CameraConfig camera) {
        String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP_FORMATTER);
        String cameraCode = cleanFileName(camera != null ? camera.getCode() : "camera");

        return AppPaths.getLogFile(
                cameraCode,
                "recommendation-" + cameraCode + "-" + timestamp + ".txt"
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
                + csv(selector.getRecommendationStatus(summary)) + ";"
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
