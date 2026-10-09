package com.ragagent.agent.compaction;

import java.util.regex.Pattern;

import com.ragagent.llm.domain.ChatResponse;

/**
 * 上下文溢出的识别。
 *
 * <p>精确识别溢出是因为恢复动作是特定的：压缩一次然后重试一次。误读成一般失败会
 * 终止整轮；误读成瞬态错误会把同一个超大的请求原样重试到次数耗尽。模式收集自真实
 * 供应商响应。</p>
 */
public final class CompactionOverflow {

    private static Pattern caseInsensitive(String regex) {
        // 需要的是 Unicode 大小写折叠，Java 里 CASE_INSENSITIVE 默认只做 ASCII 折叠，须显式加 UNICODE_CASE
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static final Pattern[] OVERFLOW_PATTERNS = {
        caseInsensitive("prompt is too long"), // Anthropic
        caseInsensitive("request_too_large"), // Anthropic (HTTP 413)
        caseInsensitive("input is too long for requested model"), // Amazon Bedrock
        caseInsensitive("exceeds the context window"), // OpenAI
        caseInsensitive("exceeds (the )?(model'?s )?maximum context length"), // OpenAI-compatible proxies
        caseInsensitive("input token count.*exceeds the maximum"), // Google Gemini
        caseInsensitive("maximum prompt length is \\d+"), // xAI Grok
        caseInsensitive("reduce the length of the messages"), // Groq
        caseInsensitive("maximum context length is \\d+ tokens"), // OpenRouter
        caseInsensitive("exceeds (the )?maximum allowed input length"), // OpenRouter / Poolside
        caseInsensitive("is longer than the model'?s context length"), // Together AI
        caseInsensitive("exceeds the limit of \\d+"), // GitHub Copilot
        caseInsensitive("exceeds the available context size"), // llama.cpp
        caseInsensitive("greater than the context length"), // LM Studio
        caseInsensitive("context window exceeds limit"), // MiniMax
        caseInsensitive("exceeded model token limit"), // Kimi
        caseInsensitive("too large for model with \\d+ maximum context length"), // Mistral
        caseInsensitive("but the configured context size is"), // DS4
        caseInsensitive("model_context_window_exceeded"), // z.ai
        caseInsensitive("prompt too long; exceeded (max )?context length"), // Ollama
        caseInsensitive("range of input length should be"), // DashScope / Qwen
        caseInsensitive("context[_ ]length[_ ]exceeded"), // generic
        caseInsensitive("too many tokens"), // generic
        caseInsensitive("token limit exceeded"), // generic
    };

    /**
     * 提到 token 计数但另有所指的错误。Bedrock 限流的
     * 措辞是 "ThrottlingException: Too many tokens, please wait before trying again"，
     * 会命中溢出模式但不是溢出。在消息任意位置匹配而非锚定，因为 WeKnora 看到的是
     * 原始供应商错误，不是预规范化的前缀。
     */
    private static final Pattern[] NON_OVERFLOW_PATTERNS = {
        caseInsensitive("throttling"),
        caseInsensitive("service unavailable"),
        caseInsensitive("rate limit"),
        caseInsensitive("too many requests"),
    };

    private CompactionOverflow() {
    }

    /**
     * 失败的 LLM 调用是否因为请求装不进上下文窗口。
     *
     * @param message 错误消息；null 视为无错误
     */
    public static boolean isOverflowError(String message) {
        if (message == null) {
            return false;
        }
        for (Pattern p : NON_OVERFLOW_PATTERNS) {
            if (p.matcher(message).find()) {
                return false;
            }
        }
        for (Pattern p : OVERFLOW_PATTERNS) {
            if (p.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 一个"成功"的响应是否实际被满窗口而不是被请求的补全预算塑形。三种形态：
     *
     * <ul>
     *   <li>供应商无声地接受了超大的请求，并上报大于窗口的 prompt（z.ai）；</li>
     *   <li>供应商截断输入恰好填满窗口、没有生成空间，以 length 停止且无输出
     *       （小米 MiMo）；</li>
     *   <li>供应商以 length 停止但产出少于我们允许的量——我们自己的上限不可能是
     *       停下的原因（一般的可恢复情形）。</li>
     * </ul>
     *
     * @param completionBudget 请求的 max_tokens（任何上下文钳制<b>之前</b>的值；
     *                        传钳制后的值会正好藏住本方法要检出的情形）
     */
    public static boolean responseHitContextLimit(
            ChatResponse resp, int contextWindow, int completionBudget) {
        if (resp == null) {
            return false;
        }
        int prompt = resp.getUsage().getPromptTokens();
        int completion = resp.getUsage().getCompletionTokens();
        boolean lengthStop = isLengthStop(resp.getFinishReason());

        if (contextWindow > 0 && prompt > contextWindow) {
            return true;
        }
        if (contextWindow > 0 && lengthStop && completion == 0 && prompt > 0
                && prompt >= contextWindow * 99 / 100) {
            return true;
        }
        // 没有 usage 就没有可比对象；把那当作溢出会把每次普通截断都重试一遍。
        return lengthStop && completionBudget > 0 && completion > 0 && completion < completionBudget;
    }

    private static boolean isLengthStop(String reason) {
        String r = reason == null ? "" : reason.trim().toLowerCase();
        return switch (r) {
            case "length", "max_tokens", "max_output_tokens" -> true;
            default -> false;
        };
    }
}
