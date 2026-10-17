package com.ragagent.contract.agent;

/**
 * 图片描述函数（原为 {@code AgentEngine.ImageDescriberFunc} 嵌套接口，上提契约面）。
 *
 * <p>工具结果里的图片经它交给 VLM 描述；实现由会话侧的装配面提供（VLM 客户端）。
 * 失败抛异常，由引擎按"图片描述不可用"处理。</p>
 */
@FunctionalInterface
public interface ImageDescriberFunc {

    /** 描述一张图片；失败抛异常。 */
    String describe(byte[] imgBytes, String prompt);
}
