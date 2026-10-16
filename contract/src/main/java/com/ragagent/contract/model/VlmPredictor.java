package com.ragagent.contract.model;

/**
 * VLM（视觉语言模型）预测句柄：单张图片 + 提示词 → 文本。
 *
 * <p><b>为什么是句柄而不是接口族</b>：VLM 调用面是"配置 + 出站通道 + 提示词"的一次函数式往返，
 * 没有多实现多实例的需要；装配（配置解析、SSRF 校验、观测装饰、命名并发闸门）
 * 由 {@link ModelRuntime#vlmPredictorFor} 一次做完，调用方只拿一个可直接调的函数。</p>
 */
@FunctionalInterface
public interface VlmPredictor {

    /** 描述一张图片；失败抛 {@link RuntimeException}（message 即对外文案）。 */
    String predict(byte[] image, String prompt);
}
