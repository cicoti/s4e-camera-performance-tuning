package com.s4etech.performance.v2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import com.s4etech.performance.v2.benchmark.GStreamerBenchmarkRunner;
import com.s4etech.performance.v2.discovery.RtspStreamDiscoveryService;
import com.s4etech.performance.v2.llm.LocalLlmAnalysisService;
import com.s4etech.performance.v2.llm.LocalLlmConfig;
import com.s4etech.performance.v2.model.CameraConfig;
import com.s4etech.performance.v2.model.PipelineTestConfig;
import com.s4etech.performance.v2.model.PipelineTestMetrics;
import com.s4etech.performance.v2.model.PipelineTestSummary;
import com.s4etech.performance.v2.model.StreamDiscoveryResult;
import com.s4etech.performance.v2.report.TuningReportService;
import com.s4etech.performance.v2.tuning.CandidateConfigGenerator;
import com.s4etech.performance.v2.tuning.RecommendationSelector;

import org.freedesktop.gstreamer.Gst;

public class AppPerformanceTuningV2 {

    private static final Path DEFAULT_LOCAL_CONFIG_FILE = Paths.get("config", "tunning.properties");

    public static void main(String[] args) {
        Gst.init("AppPerformanceTuningV2", args);

        try {
            Properties localConfig = loadLocalConfig();
            List<CameraConfig> cameras = createTestCameras(localConfig);

            RtspStreamDiscoveryService discoveryService = new RtspStreamDiscoveryService();
            GStreamerBenchmarkRunner benchmarkRunner = new GStreamerBenchmarkRunner();
            CandidateConfigGenerator candidateGenerator = new CandidateConfigGenerator();
            RecommendationSelector recommendationSelector = new RecommendationSelector();
            TuningReportService reportService = new TuningReportService();
            LocalLlmAnalysisService llmAnalysisService = new LocalLlmAnalysisService(LocalLlmConfig.from(localConfig));
            int repetitionsPerConfig = getIntConfigValue(
                    localConfig,
                    "s4e.tuning.repetitions",
                    "S4E_TUNING_REPETITIONS",
                    "tuning.repetitions",
                    3
            );

            List<StreamDiscoveryResult> discoveryResults = discoverAllCameras(cameras, discoveryService);

            if (!allCamerasAvailable(discoveryResults)) {
                printUnavailableCameras(discoveryResults);
                return;
            }

            for (StreamDiscoveryResult discoveryResult : discoveryResults) {
                CameraConfig camera = discoveryResult.getCamera();

                System.out.println();
                System.out.println("====================================================");
                System.out.println("TUNING DA CAMERA: " + camera.getCode());
                System.out.println("====================================================");

                // Preview visual desabilitado por enquanto. Reative se precisar validar a imagem da camera.
                // previewService.showPreview(discoveryResult, 5000);

                List<PipelineTestConfig> completeCandidates = candidateGenerator.generateCompleteCandidates(discoveryResult);
                List<PipelineTestSummary> completeResults = runCandidates(
                        benchmarkRunner,
                        completeCandidates,
                        repetitionsPerConfig,
                        "TUNING"
                );
                PipelineTestSummary bestFinalResult = recommendationSelector.selectBest(completeResults);

                reportService.printConsoleReport(camera, completeResults, bestFinalResult, recommendationSelector);

                Path reportFile = reportService.writeCsvReport(
                        camera,
                        completeResults,
                        bestFinalResult,
                        recommendationSelector
                );

                System.out.println();
                System.out.println("Relatorio CSV: " + reportFile.toAbsolutePath());

                runLocalLlmAnalysis(
                        llmAnalysisService,
                        camera,
                        completeResults,
                        bestFinalResult,
                        recommendationSelector
                );
            }

        } finally {
            Gst.deinit();
        }
    }

    private static List<StreamDiscoveryResult> discoverAllCameras(
            List<CameraConfig> cameras,
            RtspStreamDiscoveryService discoveryService) {

        List<StreamDiscoveryResult> results = new ArrayList<>();

        System.out.println();
        System.out.println("====================================================");
        System.out.println("PRE-CHECK DAS CAMERAS - " + cameras.size() + " configuradas");
        System.out.println("====================================================");

        for (CameraConfig camera : cameras) {
            System.out.println();
            System.out.println("Verificando camera: " + camera.getCode());
            System.out.println("URL: " + camera.getMaskedRtspUrl());

            StreamDiscoveryResult discoveryResult = discoveryService.discover(camera);
            results.add(discoveryResult);

            System.out.println("Descoberta:");
            System.out.println(discoveryResult);
        }

        return results;
    }

    private static boolean allCamerasAvailable(List<StreamDiscoveryResult> discoveryResults) {
        if (discoveryResults == null || discoveryResults.isEmpty()) {
            return false;
        }

        for (StreamDiscoveryResult discoveryResult : discoveryResults) {
            if (!discoveryResult.hasAnyProtocolAvailable()) {
                return false;
            }
        }

        return true;
    }

    private static void printUnavailableCameras(List<StreamDiscoveryResult> discoveryResults) {
        System.out.println();
        System.out.println("====================================================");
        System.out.println("TUNING ABORTADO");
        System.out.println("====================================================");
        System.out.println("Uma ou mais cameras configuradas nao responderam no pre-check.");
        System.out.println("Corrija as cameras indisponiveis antes de iniciar os testes.");

        for (StreamDiscoveryResult discoveryResult : discoveryResults) {
            if (discoveryResult.hasAnyProtocolAvailable()) {
                continue;
            }

            CameraConfig camera = discoveryResult.getCamera();

            System.out.println();
            System.out.println("Camera indisponivel: " + camera.getCode());
            System.out.println("URL: " + camera.getMaskedRtspUrl());

            if (discoveryResult.getTcpResult() != null) {
                System.out.println("TCP: " + discoveryResult.getTcpResult().getMessage());
            }

            if (discoveryResult.getUdpResult() != null) {
                System.out.println("UDP: " + discoveryResult.getUdpResult().getMessage());
            }
        }
    }

    private static List<CameraConfig> createTestCameras(Properties localConfig) {
        List<CameraConfig> cameras = new ArrayList<>();

        String singleCameraCode = getConfigValue(
                localConfig,
                "s4e.test.cameraCode",
                "S4E_TEST_CAMERA_CODE",
                "camera.code"
        );
        String singleRtspUrl = getConfigValue(
                localConfig,
                "s4e.test.rtspUrl",
                "S4E_TEST_RTSP_URL",
                "camera.rtspUrl"
        );

        if (!isBlank(singleRtspUrl)) {
            String cameraCode = isBlank(singleCameraCode) ? "CAM01" : singleCameraCode;
            cameras.add(new CameraConfig(cameraCode, singleRtspUrl));
            return cameras;
        }

        int cameraIndex = 1;

        while (true) {
            String cameraCode = localConfig.getProperty("camera." + cameraIndex + ".code");
            String rtspUrl = localConfig.getProperty("camera." + cameraIndex + ".rtspUrl");

            if (isBlank(cameraCode) && isBlank(rtspUrl)) {
                break;
            }

            if (isBlank(rtspUrl)) {
                System.out.println("Camera " + cameraIndex + " ignorada: rtspUrl nao informado.");
                cameraIndex++;
                continue;
            }

            if (isBlank(cameraCode)) {
                cameraCode = "CAM" + String.format(java.util.Locale.ROOT, "%02d", cameraIndex);
            }

            cameras.add(new CameraConfig(cameraCode, rtspUrl));
            cameraIndex++;
        }

        if (cameras.isEmpty()) {
            System.out.println("Nenhuma URL RTSP configurada.");
            System.out.println("Informe S4E_CAMERA_CONFIG_FILE, S4E_TEST_RTSP_URL ou -Ds4e.test.rtspUrl.");
        }

        return cameras;
    }

    private static Properties loadLocalConfig() {
        String configuredPath = getConfigValue(
                null,
                "s4e.camera.configFile",
                "S4E_CAMERA_CONFIG_FILE",
                null
        );
        Path configPath = isBlank(configuredPath) ? DEFAULT_LOCAL_CONFIG_FILE : Paths.get(configuredPath);
        Properties properties = new Properties();

        if (!Files.exists(configPath)) {
            System.out.println("Arquivo de configuracao local nao encontrado: " + configPath.toAbsolutePath());
            return properties;
        }

        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
            System.out.println("Configuracao local carregada: " + configPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Erro ao carregar configuracao local: " + configPath.toAbsolutePath());
            e.printStackTrace();
        }

        return properties;
    }

    private static String getConfigValue(
            Properties localConfig,
            String propertyName,
            String environmentName,
            String localConfigName) {

        String propertyValue = isBlank(propertyName) ? null : System.getProperty(propertyName);

        if (!isBlank(propertyValue)) {
            return propertyValue;
        }

        String environmentValue = System.getenv(environmentName);

        if (!isBlank(environmentValue)) {
            return environmentValue;
        }

        if (localConfig != null && !isBlank(localConfigName)) {
            return localConfig.getProperty(localConfigName);
        }

        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int getIntConfigValue(
            Properties localConfig,
            String propertyName,
            String environmentName,
            String localConfigName,
            int defaultValue) {

        String configuredValue = getConfigValue(localConfig, propertyName, environmentName, localConfigName);

        if (isBlank(configuredValue)) {
            return defaultValue;
        }

        try {
            int parsedValue = Integer.parseInt(configuredValue.trim());

            if (parsedValue < 1) {
                System.out.println("Valor invalido para " + propertyName + "/" + environmentName
                        + ": " + configuredValue + ". Usando " + defaultValue + ".");
                return defaultValue;
            }

            return parsedValue;
        } catch (NumberFormatException e) {
            System.out.println("Valor invalido para " + propertyName + "/" + environmentName
                    + ": " + configuredValue + ". Usando " + defaultValue + ".");
            return defaultValue;
        }
    }

    private static List<PipelineTestSummary> runCandidates(
            GStreamerBenchmarkRunner benchmarkRunner,
            List<PipelineTestConfig> candidates,
            int repetitionsPerConfig,
            String title) {

        List<PipelineTestSummary> summaries = new ArrayList<>();

        System.out.println();
        System.out.println("====================================================");
        System.out.println(title + " - " + candidates.size() + " configuracoes x "
                + repetitionsPerConfig + " repeticoes");
        System.out.println("====================================================");

        int index = 1;

        for (PipelineTestConfig candidate : candidates) {
            System.out.println();
            System.out.println("Configuracao " + index + "/" + candidates.size());

            List<PipelineTestMetrics> runs = new ArrayList<>();

            for (int repetition = 1; repetition <= repetitionsPerConfig; repetition++) {
                System.out.println("Repeticao " + repetition + "/" + repetitionsPerConfig);
                runs.add(benchmarkRunner.run(candidate));
            }

            PipelineTestSummary summary = new PipelineTestSummary(candidate, runs);

            System.out.println();
            System.out.println("Resumo consolidado:");
            System.out.println(summary);

            summaries.add(summary);
            index++;
        }

        return summaries;
    }

    private static void runLocalLlmAnalysis(
            LocalLlmAnalysisService llmAnalysisService,
            CameraConfig camera,
            List<PipelineTestSummary> completeResults,
            PipelineTestSummary bestFinalResult,
            RecommendationSelector recommendationSelector) {

        if (!llmAnalysisService.isEnabled()) {
            return;
        }

        System.out.println();
        System.out.println("====================================================");
        System.out.println("ANALISE LLM LOCAL");
        System.out.println("====================================================");
        System.out.println("Gerando analise explicativa. A decisao final continua sendo do algoritmo deterministico.");

        Optional<String> analysis = llmAnalysisService.analyze(
                camera,
                completeResults,
                bestFinalResult,
                recommendationSelector
        );

        if (analysis.isEmpty()) {
            System.out.println("LLM local nao retornou analise.");
            return;
        }

        System.out.println();
        System.out.println(analysis.get());

        Path llmReportFile = llmAnalysisService.writeMarkdownReport(camera, analysis.get());

        System.out.println();
        System.out.println("Relatorio LLM: " + llmReportFile.toAbsolutePath());
    }
}
