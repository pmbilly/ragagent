package com.ragagent.llm.provider;


/**
 * 服务商接口：元数据 + 配置校验。
 *
 * 校验失败抛 {@link com.ragagent.contract.error.BizException}。当前 ValidateConfig 未接线
 * （只有测试调用），见 docs/known-gaps.md 的 Provider.validateConfig 条；供运行时客户端接入时复用。
 */
public interface Provider {

    /** 返回服务商的元数据 */
    ProviderInfo info();

    /**
     * 校验服务商配置。
     *
     * @throws com.ragagent.contract.error.BizException 校验失败
     */
    void validateConfig(Config config);
}
