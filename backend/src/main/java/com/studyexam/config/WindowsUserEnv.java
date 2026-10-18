package com.studyexam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 兜底读取 Windows「用户环境变量」。
 *
 * <p>为什么需要它:用 {@code setx} 设的环境变量只对**之后启动**的进程生效。
 * 如果应用(或启动它的终端)在那之前就已经开着,进程环境里就没有这个变量——
 * 用户明明「设过了」却怎么都调不通,这是本机自用场景里最容易踩的坑之一。
 *
 * <p>因此取值顺序是:<b>进程环境变量 → Windows 用户环境变量</b>。两条路都只在本机发生,
 * 密钥不会出现在任何接口的返回里。
 *
 * <p>只处理 REG_SZ / REG_EXPAND_SZ。reg.exe 对超长值会折行(行尾补 {@code \}),这里做了拼接。
 */
final class WindowsUserEnv {

    private static final Logger log = LoggerFactory.getLogger(WindowsUserEnv.class);

    private WindowsUserEnv() {
    }

    static Optional<String> read(String name) {
        if (!isWindows() || name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", name)
                    .redirectErrorStream(true)
                    .start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            process.waitFor();
            return parse(lines);
        } catch (Exception e) {
            log.debug("读取 Windows 用户环境变量 {} 失败:{}", name, e.getMessage());
            return Optional.empty();
        }
    }

    /** 从 reg query 的输出里取出值,处理折行。 */
    static Optional<String> parse(List<String> lines) {
        StringBuilder value = null;
        for (String raw : lines) {
            String line = raw.replace("\r", "");
            if (line.isBlank()) {
                continue;
            }
            if (value == null) {
                int idx = line.indexOf("REG_SZ");
                if (idx < 0) {
                    idx = line.indexOf("REG_EXPAND_SZ");
                    if (idx < 0) {
                        continue;
                    }
                    idx += "REG_EXPAND_SZ".length();
                } else {
                    idx += "REG_SZ".length();
                }
                value = new StringBuilder(line.substring(idx).trim());
                continue;
            }
            // 续行:上一段以 \ 结尾表示值还没完
            if (value.length() > 0 && value.charAt(value.length() - 1) == '\\') {
                value.setLength(value.length() - 1);
                value.append(line.trim());
            } else {
                break;
            }
        }
        if (value == null || value.length() == 0) {
            return Optional.empty();
        }
        return Optional.of(value.toString());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}