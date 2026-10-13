package com.ragagent.session.support;

/**
 * 日志消毒：去换行/制表/控制字符，防日志注入。
 *
 * <p>原为 {@code SessionStreamController.sanitizeForLog}（阶段 5 随 QA 编排迁出 controller 包时
 * 提为中立的 support 工具——两侧都在用：控制器入口与 QA 请求解析）。</p>
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String sanitizeForLog(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // 换行/制表先归一成空格；其余控制字符直接丢弃
            if (c == '\n' || c == '\r' || c == '\t') {
                builder.append(' ');
            } else if (c >= 32) {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
