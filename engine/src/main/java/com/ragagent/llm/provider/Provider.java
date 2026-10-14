package com.ragagent.llm.provider;


/**
 * 服务商接口：元数据 + 配置校验。
 *
 * 校验失败抛 {@link com.ragagent.common.error.BizException}。当前 ValidateConfig 尚无生产调用点（仅测试），
 * 供运行时客户端接入时复用。
 */
public interface Provider {

    /** 返回服务商的元数据 */
    ProviderInfo info();

    /**
     * 校验服务商配置。
     *
     * @throws com.ragagent.common.error.BizException 校验失败
     */
    void validateConfig(Config config);
}
