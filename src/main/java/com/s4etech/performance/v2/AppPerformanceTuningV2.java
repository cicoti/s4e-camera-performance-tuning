package com.s4etech.performance.v2;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import com.s4etech.performance.v2.benchmark.GStreamerBenchmarkRunner;
import com.s4etech.performance.v2.discovery.RtspStreamDiscoveryService;
import com.s4etech.performance.v2.llm.LocalLlmAnalysisService;
import com.s4etech.performance.v2.llm.LocalLlmConfig;
import com.s4etech.performance.v2.model.CameraConfig;
import com.s4etech.performance.v2.model.PipelineTestConfig;
import com.s4etech.performance.v2.model.PipelineTestMetrics;
import com.s4etech.performance.v2.model.PipelineTestSummary;
import com.s4etech.performance.v2.model.StreamDiscoveryResult;
import com.s4etech.performance.v2.precheck.GStreamerPrecheckService;
import com.s4etech.performance.v2.report.TuningReportService;
import com.s4etech.performance.v2.tuning.CandidateConfigGenerator;
import com.s4etech.performance.v2.tuning.RecommendationSelector;

import org.freedesktop.gstreamer.Gst;

public class AppPerformanceTuningV2 {

    private static final Path DEFAULT_LOCAL_CONFIG_FILE = Paths.get("config", "tunning.properties");
    private static final String TRAY_ICON_RESOURCE = "/icons/app-icon.png";
    private static TrayIcon trayIcon;

    public static void main(String[] args) {
        if (!initializeGStreamer(args)) {
            return;
        }

        installTrayIcon();

        try {
            System.out.println("Diretorio da aplicacao: " + AppPaths.getAppDir());
            Properties localConfig = loadLocalConfig();
            List<CameraConfig> cameras = createTestCameras(localConfig);

            RtspStreamDiscoveryService discoveryService = new RtspStreamDiscoveryService();
            GStreamerBenchmarkRunner benchmarkRunner = new GStreamerBenchmarkRunner();
            CandidateConfigGenerator candidateGenerator = new CandidateConfigGenerator();
            RecommendationSelector recommendationSelector = new RecommendationSelector();
            TuningReportService reportService = new TuningReportService();
            LocalLlmAnalysisService llmAnalysisService = new LocalLlmAnalysisService(LocalLlmConfig.from(localConfig));
            llmAnalysisService.prepare();

            if (!new GStreamerPrecheckService().run()) {
                System.out.println();
                System.out.println("Tuning abortado: GStreamer nao passou no pre-check.");
                return;
            }

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

                Optional<String> llmAnalysis = runLocalLlmAnalysis(
                        llmAnalysisService,
                        camera,
                        completeResults,
                        bestFinalResult,
                        recommendationSelector
                );

                Path recommendationReportFile = reportService.writeRecommendationTextReport(
                        camera,
                        completeResults,
                        bestFinalResult,
                        recommendationSelector,
                        llmAnalysis
                );

                System.out.println();
                System.out.println("Relatorio TXT recomendacao: " + recommendationReportFile.toAbsolutePath());
            }

        } finally {
            removeTrayIcon();
            Gst.deinit();
            System.out.println("Aplicacao encerrada.");
        }
    }

    private static boolean initializeGStreamer(String[] args) {
        try {
            Gst.init("AppPerformanceTuningV2", args);
            return true;
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            System.out.println();
            System.out.println("====================================================");
            System.out.println("PRE-CHECK DO GSTREAMER");
            System.out.println("====================================================");
            System.out.println("Nao foi possivel inicializar o GStreamer.");
            System.out.println("Erro: " + e.getMessage());
            System.out.println("Verifique se o GStreamer esta instalado e disponivel no PATH.");
            return false;
        }
    }

    private static void installTrayIcon() {
        if (!SystemTray.isSupported()) {
            System.out.println("Bandeja do sistema nao suportada neste ambiente.");
            return;
        }

        try (InputStream iconStream = AppPerformanceTuningV2.class.getResourceAsStream(TRAY_ICON_RESOURCE)) {
            if (iconStream == null) {
                Image externalImage = loadExternalTrayIcon();

                if (externalImage == null) {
                    System.out.println("Icone da bandeja nao encontrado: " + TRAY_ICON_RESOURCE);
                    System.out.println("Tambem procurei em: "
                            + AppPaths.getAppDir().resolve("app-icon.png") + ", "
                            + AppPaths.getAppDir().resolve("app-icon.ico") + ", "
                            + AppPaths.getAppDir().resolve("icons").resolve("app-icon.png") + ", "
                            + AppPaths.getAppDir().resolve("icons").resolve("app-icon.ico"));
                    return;
                }

                addTrayIcon(externalImage);
                return;
            }

            Image image = ImageIO.read(iconStream);
            addTrayIcon(image);
        } catch (AWTException | IOException | RuntimeException e) {
            System.out.println("Nao foi possivel adicionar icone na bandeja: " + e.getMessage());
        }
    }

    private static Image loadExternalTrayIcon() {
        List<Path> iconFiles = List.of(
                AppPaths.getAppDir().resolve("icons").resolve("app-icon.png"),
                AppPaths.getAppDir().resolve("app-icon.png"),
                AppPaths.getAppDir().resolve("icons").resolve("app-icon.ico"),
                AppPaths.getAppDir().resolve("app-icon.ico")
        );

        for (Path iconFile : iconFiles) {
            Image image = loadImageFile(iconFile);

            if (image != null) {
                System.out.println("Icone da bandeja carregado: " + iconFile.toAbsolutePath());
                return image;
            }
        }

        return null;
    }

    private static Image loadImageFile(Path iconFile) {
        if (!Files.exists(iconFile)) {
            return null;
        }

        try {
            if (iconFile.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png")) {
                return ImageIO.read(iconFile.toFile());
            }

            ImageIcon imageIcon = new ImageIcon(iconFile.toString());

            if (imageIcon.getIconWidth() > 0 && imageIcon.getIconHeight() > 0) {
                return imageIcon.getImage();
            }
        } catch (RuntimeException | IOException e) {
            System.out.println("Nao foi possivel carregar icone " + iconFile.toAbsolutePath()
                    + ": " + e.getMessage());
        }

        return null;
    }

    private static void addTrayIcon(Image image) throws AWTException {
        PopupMenu popupMenu = new PopupMenu();
        MenuItem exitItem = new MenuItem("Sair");

        exitItem.addActionListener(event -> exitFromTray());
        popupMenu.add(exitItem);

        trayIcon = new TrayIcon(image, "S4E Camera Performance Tuning", popupMenu);
        trayIcon.setImageAutoSize(true);

        SystemTray.getSystemTray().add(trayIcon);
    }

    private static void exitFromTray() {
        Thread exitThread = new Thread(() -> {
            System.out.println();
            System.out.println("Encerramento solicitado pela bandeja do sistema.");
            System.out.println("Finalizando aplicacao...");
            System.out.flush();

            Thread forcedExitThread = new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                System.out.println("Forcando encerramento da aplicacao.");
                System.out.flush();
                Runtime.getRuntime().halt(0);
            }, "tray-forced-exit");

            forcedExitThread.setDaemon(true);
            forcedExitThread.start();

            System.exit(0);
        }, "tray-exit");

        exitThread.setDaemon(false);
        exitThread.start();
    }

    private static void removeTrayIcon() {
        if (trayIcon == null || !SystemTray.isSupported()) {
            return;
        }

        SystemTray.getSystemTray().remove(trayIcon);
        trayIcon = null;
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

        addNamedCameraProperties(localConfig, cameras);
        addIndexedCameraProperties(localConfig, cameras);

        if (cameras.isEmpty()) {
            System.out.println("Nenhuma URL RTSP configurada.");
            System.out.println("Informe S4E_CAMERA_CONFIG_FILE, S4E_TEST_RTSP_URL ou -Ds4e.test.rtspUrl.");
        }

        return cameras;
    }

    private static void addNamedCameraProperties(Properties localConfig, List<CameraConfig> cameras) {
        if (localConfig == null) {
            return;
        }

        localConfig.stringPropertyNames().stream()
                .filter(AppPerformanceTuningV2::isNamedCameraProperty)
                .sorted(Comparator.naturalOrder())
                .forEach(propertyName -> {
                    String cameraCode = propertyName.substring("camera.".length());
                    String rtspUrl = localConfig.getProperty(propertyName);

                    if (isBlank(rtspUrl)) {
                        System.out.println("Camera " + cameraCode + " ignorada: rtspUrl nao informado.");
                        return;
                    }

                    cameras.add(new CameraConfig(cameraCode, rtspUrl));
                });
    }

    private static boolean isNamedCameraProperty(String propertyName) {
        if (isBlank(propertyName) || !propertyName.startsWith("camera.")) {
            return false;
        }

        String suffix = propertyName.substring("camera.".length());

        return !suffix.isBlank()
                && !"code".equals(suffix)
                && !"rtspUrl".equals(suffix)
                && !suffix.matches("\\d+\\.(code|rtspUrl)");
    }

    private static void addIndexedCameraProperties(Properties localConfig, List<CameraConfig> cameras) {
        if (localConfig == null) {
            return;
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
    }

    private static Properties loadLocalConfig() {
        String configuredPath = getConfigValue(
                null,
                "s4e.camera.configFile",
                "S4E_CAMERA_CONFIG_FILE",
                null
        );
        Path configPath = isBlank(configuredPath)
                ? AppPaths.getConfigFile(DEFAULT_LOCAL_CONFIG_FILE.getFileName().toString())
                : resolveConfiguredPath(configuredPath);
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

    private static Path resolveConfiguredPath(String configuredPath) {
        Path path = Paths.get(configuredPath);

        if (path.isAbsolute()) {
            return path;
        }

        return AppPaths.getAppDir().resolve(path).normalize();
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

    private static Optional<String> runLocalLlmAnalysis(
            LocalLlmAnalysisService llmAnalysisService,
            CameraConfig camera,
            List<PipelineTestSummary> completeResults,
            PipelineTestSummary bestFinalResult,
            RecommendationSelector recommendationSelector) {

        if (!llmAnalysisService.isEnabled()) {
            return Optional.empty();
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
            return Optional.empty();
        }

        System.out.println();
        System.out.println(analysis.get());

        Path llmReportFile = llmAnalysisService.writeMarkdownReport(camera, analysis.get());

        System.out.println();
        System.out.println("Relatorio LLM: " + llmReportFile.toAbsolutePath());

        return analysis;
    }
}
