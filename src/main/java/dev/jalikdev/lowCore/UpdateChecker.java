package dev.jalikdev.lowCore;

import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class UpdateChecker {

    private final LowCore plugin;
    private final Logger logger;
    private final AtomicBoolean running = new AtomicBoolean();

    private static final String API_URL = "https://api.github.com/repos/jan-kulik/LowCore/releases/latest";

    public UpdateChecker(LowCore plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void checkForUpdates() {
        if (!running.compareAndSet(false, true)) return;
        String currentVersion = plugin.getPluginMeta().getVersion();
        boolean notifyConsole = plugin.getConfig().getBoolean("update-checker.notify-console", true);
        plugin.setUpdateAvailable(false);
        plugin.setLatestVersion(null);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) URI.create(API_URL).toURL().openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "LowCore-UpdateChecker");
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    logger.warning("Could not check for updates: HTTP " + responseCode);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (sb.length() + line.length() > 65_536) {
                            throw new IllegalStateException("GitHub response exceeded 64 KiB");
                        }
                        sb.append(line);
                    }
                }

                String json = sb.toString();
                String latestVersion = parseTagName(json);
                if (latestVersion == null || latestVersion.isEmpty()) {
                    logger.warning("Could not parse latest version from GitHub response.");
                    return;
                }

                if (latestVersion.startsWith("v")) {
                    latestVersion = latestVersion.substring(1);
                }

                if (isNewerVersion(latestVersion, currentVersion)) {
                    plugin.setUpdateAvailable(true);
                    plugin.setLatestVersion(latestVersion);

                    if (notifyConsole) {
                        logger.info("============================================");
                        logger.info("A new version of LowCore is available!");
                        logger.info("Current version: " + currentVersion);
                        logger.info("Latest version:  " + latestVersion);
                        logger.info("Download: https://github.com/jan-kulik/LowCore/releases");
                        logger.info("============================================");
                    }
                } else {
                    logger.info("LowCore is up to date (version " + currentVersion + ").");
                }

            } catch (Exception e) {
                logger.warning("Error while checking for updates: " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
                running.set(false);
            }
        });
    }

    private String parseTagName(String json) {
        String marker = "\"tag_name\"";
        int index = json.indexOf(marker);
        if (index == -1) return null;
        int colon = json.indexOf(':', index + marker.length());
        if (colon == -1) return null;
        int start = json.indexOf('"', colon + 1);
        if (start == -1) return null;
        start++;
        int end = json.indexOf('"', start);
        if (end == -1) return null;

        return json.substring(start, end);
    }

    private boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");

        int max = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < max; i++) {
            int latestNum = i < latestParts.length ? parseIntSafe(latestParts[i]) : 0;
            int currentNum = i < currentParts.length ? parseIntSafe(currentParts[i]) : 0;

            if (latestNum > currentNum) return true;
            if (latestNum < currentNum) return false;
        }
        return false;
    }

    private int parseIntSafe(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
