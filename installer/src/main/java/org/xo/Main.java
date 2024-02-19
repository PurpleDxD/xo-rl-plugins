package org.xo;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class Main {

    private static final String ORIGINAL_MAIN = "net.runelite.launcher.Launcher";
    private static final String NEW_MAIN = "org.xo.LauncherHijack";

    private static final Path RL_PATH = Path.of(System.getProperty("user.home"), ".runelite");
    private static final Path PLUGINS_PATH = RL_PATH.resolve("sideloaded-plugins");
    private static final Path PLUGINS_FILE = PLUGINS_PATH.resolve("XoPlugins.jar");
    private static final Path CACHE_PATH = RL_PATH.resolve("cache");
    private static final Path JAGEX_CACHE_PATH = RL_PATH.resolve("jagexcache");
    private static final Path LOGS_PATH = RL_PATH.resolve("logs");
    private static final Path PACKET_UTILS_PATH = RL_PATH.resolve("PacketUtils");

    private static final Path RL_CONFIG_PATH = Path.of(System.getProperty("user.home"), "AppData", "Local", "RuneLite");
    private static final Path CONFIG_FILE = RL_CONFIG_PATH.resolve("config.json");
    private static final Path CONFIG_FILE_BACKUP = RL_CONFIG_PATH.resolve("config.json.bkp");
    private static final Path HIJACKER_FILE = RL_CONFIG_PATH.resolve("XoHijacker.jar");

    public static void main(String[] args) {
        try {
            if (shouldInstall()) {
                install();
            } else {
                remove();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static boolean shouldInstall() throws IOException {
        JSONObject object = getConfigObject();

        return ORIGINAL_MAIN.equals(object.getString("mainClass"));
    }

    private static void modifyConfigFile() throws IOException {
        JSONObject object = getConfigObject();

        object.remove("mainClass");
        object.put("mainClass", NEW_MAIN);
        object.remove("classPath");
        object.append("classPath", "XoHijacker.jar");
        object.append("classPath", "RuneLite.jar");

        Files.copy(CONFIG_FILE, CONFIG_FILE_BACKUP);

        try (FileWriter fileWriter = new FileWriter(CONFIG_FILE.toFile())) {
            fileWriter.write(object.toString());
            fileWriter.flush();
        }
    }

    private static void extractIfNeeded(Path destination) throws IOException {
        if (!isFileExists(destination)) {
            extractInnerJar(destination);
        }
    }

    private static boolean isFileExists(Path path) throws IOException {
        try (Stream<Path> stream = Files.list(path.getParent())) {
            return stream.anyMatch(p -> p.getFileName().equals(path.getFileName()));
        }
    }

    private static void extractInnerJar(Path destination) throws IOException {
        String fileName = destination.getFileName().toString();
        try (InputStream is = Main.class.getResourceAsStream("/dependencies/" + fileName)) {
            if (is != null) {
                try (OutputStream os = Files.newOutputStream(destination)) {
                    IOUtils.copy(is, os);
                }
            } else {
                throw new IOException("Inner JAR file not found: " + fileName);
            }
        }
    }

    private static void install() throws IOException {
        FileUtils.forceMkdir(PLUGINS_PATH.toFile());
        extractIfNeeded(PLUGINS_FILE);
        extractIfNeeded(HIJACKER_FILE);
        modifyConfigFile();
    }

    private static void remove() throws IOException {
        FileUtils.deleteDirectory(PLUGINS_PATH.toFile());
        FileUtils.deleteDirectory(CACHE_PATH.toFile());
        FileUtils.deleteDirectory(JAGEX_CACHE_PATH.toFile());
        FileUtils.deleteDirectory(LOGS_PATH.toFile());
        FileUtils.deleteDirectory(PACKET_UTILS_PATH.toFile());
        FileUtils.forceDelete(HIJACKER_FILE.toFile());
        FileUtils.forceDelete(CONFIG_FILE.toFile());
        FileUtils.moveFile(CONFIG_FILE_BACKUP.toFile(), CONFIG_FILE.toFile());
    }

    private static JSONObject getConfigObject() throws IOException {
        JSONObject object;
        try (InputStream inputStream = new FileInputStream(CONFIG_FILE.toFile())) {
            JSONTokener tokener = new JSONTokener(inputStream);
            object = new JSONObject(tokener);
        }
        return object;
    }

}