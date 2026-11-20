package com.ragagent.platform.prompt;

import java.util.List;

/**
 * 系统提示词的组装原语（L1 唯一实现）。
 *
 * <p>三条链路（agent / chatpipeline 快答 / session fallback）各自决定"选哪些段"，
 * 但"怎么拼"只在这里定义：分节连接规则、源数据段尾巴、协议段接入、右侧裁剪。
 * 此前这四条规则在四处各写一份（{@code AgentPrompts.renderSystemPromptSections}、
 * {@code PipelineCommon}、{@code ReferencesSupport}、{@code SessionQaFallback}），
 * 改一处漏一处就出现口径漂移。</p>
 *
 * <p><b>字节即契约</b>：这里的拼接结果直接进模型输入，由实录（{@code AgentPromptGolden*}）逐字节钉住；
 * 任何改动都会在实录上立刻变红，属显式契约变更。</p>
 */
public final class PromptComposer {

    /** 源数据边界 + 有据作答两段（含前导分隔符）——快答与 fallback 共用的系统提示词尾巴。 */
    public static final String SOURCES_AND_OUTPUT_SUFFIX = "\n\n"
            + PromptConstants.SOURCE_DATA_BOUNDARY_PROMPT
            + "\n\n" + PromptConstants.SOURCED_ANSWER_OUTPUT_PROMPT;

    private PromptComposer() {
    }

    /** 分节渲染：逐段 {@code trim}、跳过空段、以 {@code "\n\n"} 连接；空列表 → {@code ""}。 */
    public static String joinSections(List<String> sections) {
        if (sections == null || sections.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String raw : sections) {
            String content = raw == null ? "" : raw.trim();
            if (content.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append("\n\n");
            }
            out.append(content);
        }
        return out.toString();
    }

    /**
     * 把 system-owned 协议段接到系统提示词末尾：先右侧裁剪，再直接接
     * （协议段自带前导换行，故不再补分隔符——保持既有字节）。
     */
    public static String appendProtocol(String systemPrompt, String protocolPrompt) {
        return rightTrim(systemPrompt) + (protocolPrompt == null ? "" : protocolPrompt);
    }

    /** 右侧裁掉空白（空格 / 制表 / CR / LF）；{@code null} → {@code ""}。 */
    public static String rightTrim(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0) {
            char c = value.charAt(end - 1);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                end--;
            } else {
                break;
            }
        }
        return value.substring(0, end);
    }
}
