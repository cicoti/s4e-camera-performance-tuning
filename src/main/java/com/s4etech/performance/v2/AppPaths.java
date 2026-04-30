package com.s4etech.performance.v2;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class AppPaths {

    private static final String APP_DIR_PROPERTY = "s4e.app.dir";
    private static final String APP_DIR_ENVIRONMENT = "S4E_APP_DIR";
    private static final Path APP_DIR = resolveAppDir();

    private AppPaths() {
    }

    public static Path getAppDir() {
        return APP_DIR;
    }

    public static Path getConfigFile(String fileName) {
        return APP_DIR.resolve("config").resolve(fileName);
    }

    public static Path getLogFile(String first, String... more) {
        return APP_DIR.resolve("logs").resolve(Paths.get(first, more));
    }

    private static Path resolveAppDir() {
        Path configuredPath = getConfiguredAppDir();

        if (configuredPath != null) {
            return configuredPath;
        }

        Path codeSourcePath = getCodeSourcePath();

        if (codeSourcePath == null) {
            return Paths.get("").toAbsolutePath().normalize();
        }

        Path codeSourceDir = getCodeSourceDir(codeSourcePath);

        if (isDevelopmentClassesDir(codeSourceDir)) {
            return Paths.get("").toAbsolutePath().normalize();
        }

        return codeSourceDir;
    }

    private static Path getConfiguredAppDir() {
        String propertyValue = System.getProperty(APP_DIR_PROPERTY);

        if (!isBlank(propertyValue)) {
            return Paths.get(propertyValue).toAbsolutePath().normalize();
        }

        String environmentValue = System.getenv(APP_DIR_ENVIRONMENT);

        if (!isBlank(environmentValue)) {
            return Paths.get(environmentValue).toAbsolutePath().normalize();
        }

        return null;
    }

    private static Path getCodeSourcePath() {
        try {
            return Paths.get(AppPaths.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toAbsolutePath()
                    .normalize();
        } catch (NullPointerException | SecurityException | URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private static Path getCodeSourceDir(Path codeSourcePath) {
        if (java.nio.file.Files.isRegularFile(codeSourcePath)) {
            return codeSourcePath.getParent();
        }

        return codeSourcePath;
    }

    private static boolean isDevelopmentClassesDir(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }

        Path parent = path.getParent();

        return "classes".equals(path.getFileName().toString())
                && parent != null
                && parent.getFileName() != null
                && "target".equals(parent.getFileName().toString());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
