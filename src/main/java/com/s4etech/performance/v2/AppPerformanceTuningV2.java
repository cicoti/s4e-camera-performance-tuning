package com.s4etech.performance.v2;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.s4etech.performance.v2.benchmark.GStreamerBenchmarkRunner;
import com.s4etech.performance.v2.discovery.RtspStreamDiscoveryService;
import com.s4etech.performance.v2.model.CameraConfig;
import com.s4etech.performance.v2.model.PipelineTestConfig;
import com.s4etech.performance.v2.model.PipelineTestMetrics;
import com.s4etech.performance.v2.model.StreamDiscoveryResult;
import com.s4etech.performance.v2.report.TuningReportService;
import com.s4etech.performance.v2.tuning.CandidateConfigGenerator;
import com.s4etech.performance.v2.tuning.RecommendationSelector;

import org.freedesktop.gstreamer.Gst;

public class AppPerformanceTuningV2 {

    public static void main(String[] args) {
        Gst.init("AppPerformanceTuningV2", args);

        try {
            List<CameraConfig> cameras = createTestCameras();

            RtspStreamDiscoveryService discoveryService = new RtspStreamDiscoveryService();
            GStreamerBenchmarkRunner benchmarkRunner = new GStreamerBenchmarkRunner();
            CandidateConfigGenerator candidateGenerator = new CandidateConfigGenerator();
            RecommendationSelector recommendationSelector = new RecommendationSelector();
            TuningReportService reportService = new TuningReportService();

            for (CameraConfig camera : cameras) {
                System.out.println();
                System.out.println("====================================================");
                System.out.println("Câmera: " + camera.getCode());
                System.out.println("URL: " + camera.getMaskedRtspUrl());
                System.out.println("====================================================");

                StreamDiscoveryResult discoveryResult = discoveryService.discover(camera);

                System.out.println("Descoberta:");
                System.out.println(discoveryResult);

                if (!discoveryResult.hasAnyProtocolAvailable()) {
                    System.out.println("Nenhum protocolo disponível para esta câmera.");
                    continue;
                }

                // Preview visual desabilitado por enquanto. Reative se precisar validar a imagem da camera.
                // previewService.showPreview(discoveryResult, 5000);

                List<PipelineTestConfig> completeCandidates = candidateGenerator.generateCompleteCandidates(discoveryResult);
                List<PipelineTestMetrics> completeResults = runCandidates(
                        benchmarkRunner,
                        completeCandidates,
                        "TUNING"
                );
                PipelineTestMetrics bestFinalResult = recommendationSelector.selectBest(completeResults);

                reportService.printConsoleReport(camera, completeResults, bestFinalResult, recommendationSelector);

                Path reportFile = reportService.writeCsvReport(
                        camera,
                        completeResults,
                        bestFinalResult,
                        recommendationSelector
                );

                System.out.println();
                System.out.println("Relatorio CSV: " + reportFile.toAbsolutePath());
            }

        } finally {
            Gst.deinit();
        }
    }

    private static List<CameraConfig> createTestCameras() {
        List<CameraConfig> cameras = new ArrayList<>();
        String cameraCode = getConfigValue("s4e.test.cameraCode", "S4E_TEST_CAMERA_CODE");
        String rtspUrl = getConfigValue("s4e.test.rtspUrl", "S4E_TEST_RTSP_URL");

        if (isBlank(cameraCode)) {
            cameraCode = "CAM01";
        }

        if (isBlank(rtspUrl)) {
            System.out.println("Nenhuma URL RTSP configurada.");
            System.out.println("Informe S4E_TEST_RTSP_URL ou -Ds4e.test.rtspUrl para executar o tuning.");
            return cameras;
        }

        cameras.add(new CameraConfig(cameraCode, rtspUrl));

        return cameras;
    }

    private static String getConfigValue(String propertyName, String environmentName) {
        String propertyValue = System.getProperty(propertyName);

        if (!isBlank(propertyValue)) {
            return propertyValue;
        }

        return System.getenv(environmentName);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static List<PipelineTestMetrics> runCandidates(
            GStreamerBenchmarkRunner benchmarkRunner,
            List<PipelineTestConfig> candidates,
            String title) {

        List<PipelineTestMetrics> results = new ArrayList<>();

        System.out.println();
        System.out.println("====================================================");
        System.out.println(title + " - " + candidates.size() + " configuracoes");
        System.out.println("====================================================");

        int index = 1;

        for (PipelineTestConfig candidate : candidates) {
            System.out.println();
            System.out.println("Configuracao " + index + "/" + candidates.size());
            results.add(benchmarkRunner.run(candidate));
            index++;
        }

        return results;
    }

}
