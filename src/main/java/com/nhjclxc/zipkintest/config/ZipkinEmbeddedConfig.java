package com.nhjclxc.zipkintest.config;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.web.client.RestTemplate;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.lang.ProcessBuilder;
import java.lang.Process;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内嵌Zipkin配置
 * 在应用启动时自动启动Zipkin服务，应用关闭时自动停止
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "zipkin.embedded.enabled", havingValue = "true", matchIfMissing = true)
public class ZipkinEmbeddedConfig implements CommandLineRunner, ApplicationListener<ContextClosedEvent> {

    private Process zipkinProcess;
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private static final int ZIPKIN_PORT = 9411;
    private static final String ZIPKIN_PROCESS_NAME = "zipkin";

    @Value("${zipkin.embedded.enabled}")
    private Boolean enabled;

    // 添加启动完成标志
    private static volatile boolean zipkinReady = false;

    /**
     * 配置RestTemplate Bean，用于Sleuth发送数据到Zipkin
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 检查Zipkin是否已就绪
     */
    public static boolean isZipkinReady() {
        return zipkinReady;
    }

    @Override
    @Order(1) // 优先启动
    public void run(String... args) throws Exception {
        log.info("=== ZipkinEmbeddedConfig 开始执行 ===");
        log.info("当前工作目录: {}", System.getProperty("user.dir"));
        log.info("Java版本: {}", System.getProperty("java.version"));
        log.info("Java路径: {}", System.getProperty("java.home"));

        // 检查配置
        log.info("zipkin.embedded.enabled 配置: {}", enabled);
        if (!enabled) {
            return;
        }

        startZipkinServer();
    }

    /**
     * 启动Zipkin服务
     */
    private void startZipkinServer() {
        try {
            log.info("=== 开始启动内嵌Zipkin服务 ===");

            // 获取zipkin.jar的路径
            String projectDir = System.getProperty("user.dir");
            String zipkinJarPath = projectDir + File.separator + "lib" + File.separator + "zipkin.jar";

            log.info("项目目录: {}", projectDir);
            log.info("Zipkin JAR路径: {}", zipkinJarPath);

            File zipkinJar = new File(zipkinJarPath);
            log.info("Zipkin JAR文件存在: {}", zipkinJar.exists());
            if (zipkinJar.exists()) {
                log.info("Zipkin JAR文件大小: {} bytes", zipkinJar.length());
            }

            if (!zipkinJar.exists()) {
                log.error("未找到zipkin.jar文件: {}", zipkinJarPath);
                log.info("请运行 download-zipkin.bat 下载zipkin.jar文件");
                return;
            }

            // 清理可能存在的旧进程
            cleanupExistingZipkinProcesses();

            // 检查端口是否被占用
            if (!checkPortAvailability(ZIPKIN_PORT)) {
                log.error("端口 {} 被占用，无法启动Zipkin服务", ZIPKIN_PORT);
                return;
            }

            // 构建启动命令
            String[] command = {
                    "java", "-jar", zipkinJarPath,
                    "--server.port=" + ZIPKIN_PORT,
                    "--logging.level.zipkin=INFO"
            };

            log.info("启动命令: {}", String.join(" ", command));

            ProcessBuilder processBuilder = new ProcessBuilder(command);

            // 设置工作目录
            processBuilder.directory(new File(projectDir));
            log.info("工作目录: {}", processBuilder.directory().getAbsolutePath());

            // 不合并错误和标准输出，分别读取
            processBuilder.redirectErrorStream(false);

            // 启动进程
            log.info("正在启动Zipkin进程...");
            zipkinProcess = processBuilder.start();
            log.info("Zipkin进程已启动，PID: {}", getProcessId(zipkinProcess));

            // 立即开始读取输出
            startOutputReader();

            // 等待启动并检查服务可用性
            if (waitForZipkinStartup()) {
                log.info("✅ Zipkin服务启动成功！");
                log.info("访问地址: http://localhost:{}", ZIPKIN_PORT);
                // 设置Zipkin就绪标志
                zipkinReady = true;
                log.info("✅ Zipkin就绪标志已设置，Sleuth可以开始发送数据");
            } else {
                log.error("❌ Zipkin服务启动失败");
                if (zipkinProcess != null) {
                    int exitCode = zipkinProcess.exitValue();
                    log.error("进程退出码: {}", exitCode);
                    readRemainingOutput();
                }
            }

        } catch (Exception e) {
            log.error("启动Zipkin服务异常", e);
            e.printStackTrace();
        }
    }

    /**
     * 清理已存在的Zipkin进程
     */
    private void cleanupExistingZipkinProcesses() {
        try {
            log.info("检查并清理已存在的Zipkin进程...");

            // Windows系统
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                ProcessBuilder pb = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq java.exe", "/FO", "CSV");
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("zipkin")) {
                        log.info("发现Zipkin进程: {}", line);
                        // 这里可以添加强制终止逻辑
                    }
                }
            } else {
                // Linux/Unix系统
                ProcessBuilder pb = new ProcessBuilder("ps", "aux");
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("zipkin.jar")) {
                        log.info("发现Zipkin进程: {}", line);
                        // 这里可以添加强制终止逻辑
                    }
                }
            }
        } catch (Exception e) {
            log.warn("清理Zipkin进程时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 检查端口是否可用
     */
    private boolean checkPortAvailability(int port) {
        try {
            log.info("检查端口 {} 是否被占用...", port);

            // 尝试连接端口
            try (Socket socket = new Socket("localhost", port)) {
                log.warn("端口 {} 已被占用", port);
                return false;
            } catch (Exception e) {
                log.info("端口 {} 可用", port);
                return true;
            }
        } catch (Exception e) {
            log.warn("检查端口时发生异常: {}", e.getMessage());
            return true; // 异常情况下假设端口可用
        }
    }

    /**
     * 等待Zipkin启动
     */
    private boolean waitForZipkinStartup() {
        log.info("等待Zipkin服务启动...");

        // 最多等待30秒
        for (int i = 0; i < 30; i++) {
            try {
                Thread.sleep(1000);

                // 检查进程是否存活
                if (zipkinProcess == null || !zipkinProcess.isAlive()) {
                    log.error("Zipkin进程已停止");
                    return false;
                }

                // 检查端口是否可访问
                if (checkZipkinHealth()) {
                    log.info("Zipkin服务已就绪");
                    return true;
                }

            } catch (InterruptedException e) {
                log.error("等待Zipkin启动被中断", e);
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.error("Zipkin服务启动超时");
        return false;
    }

    /**
     * 检查Zipkin健康状态
     */
    private boolean checkZipkinHealth() {
        try {
            // 尝试连接Zipkin的health端点
            try (Socket socket = new Socket("localhost", ZIPKIN_PORT)) {
                return true;
            } catch (Exception e) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 启动输出读取器
     */
    private void startOutputReader() {
        // 读取标准输出
        Thread stdoutThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(zipkinProcess.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null && !isShuttingDown.get()) {
                    // 只记录重要的启动信息，过滤掉重复的日志
                    if (line.contains("Started") || line.contains("Serving HTTP") || line.contains("ERROR") || line.contains("WARN")) {
                        log.info("Zipkin: {}", line);
                    }
                }
            } catch (Exception e) {
                if (!isShuttingDown.get()) {
                    log.warn("读取标准输出异常: {}", e.getMessage());
                }
            }
        });
        stdoutThread.setDaemon(true);
        stdoutThread.start();

        // 读取错误输出
        Thread stderrThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(zipkinProcess.getErrorStream()));
                String line;
                while ((line = reader.readLine()) != null && !isShuttingDown.get()) {
                    // 只记录错误信息
                    if (line.contains("ERROR") || line.contains("Exception") || line.contains("Failed")) {
                        log.error("Zipkin错误: {}", line);
                    }
                }
            } catch (Exception e) {
                if (!isShuttingDown.get()) {
                    log.warn("读取错误输出异常: {}", e.getMessage());
                }
            }
        });
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    /**
     * 读取剩余输出
     */
    private void readRemainingOutput() {
        try {
            // 读取标准输出
            BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(zipkinProcess.getInputStream()));
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                log.info("Zipkin剩余输出: {}", line);
            }

            // 读取错误输出
            BufferedReader stderrReader = new BufferedReader(new InputStreamReader(zipkinProcess.getErrorStream()));
            while ((line = stderrReader.readLine()) != null) {
                log.error("Zipkin剩余错误: {}", line);
            }
        } catch (Exception e) {
            log.warn("读取剩余输出异常: {}", e.getMessage());
        }
    }

    /**
     * 获取进程ID
     */
    private String getProcessId(Process process) {
        try {
            // 简化版本，直接返回进程对象的hashCode作为标识
            return String.valueOf(process.hashCode());
        } catch (Exception e) {
            log.warn("获取进程ID失败: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * 应用关闭时停止Zipkin服务
     */
    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("=== 应用关闭，停止Zipkin服务 ===");
        isShuttingDown.set(true);
        // 重置Zipkin就绪标志
        zipkinReady = false;
        stopZipkinServer();
    }

    /**
     * 停止Zipkin服务
     */
    public void stopZipkinServer() {
        if (zipkinProcess != null && zipkinProcess.isAlive()) {
            log.info("正在停止Zipkin服务...");

            try {
                // 先尝试优雅关闭
                zipkinProcess.destroy();

                // 等待进程结束，最多等待10秒
                boolean terminated = zipkinProcess.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

                if (!terminated) {
                    log.warn("Zipkin进程未在10秒内结束，强制终止...");
                    zipkinProcess.destroyForcibly();
                    zipkinProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                }

                log.info("Zipkin服务已停止");

            } catch (InterruptedException e) {
                log.error("停止Zipkin服务异常", e);
                Thread.currentThread().interrupt();
            }
        } else {
            log.info("Zipkin进程不存在或已停止");
        }
    }
}
