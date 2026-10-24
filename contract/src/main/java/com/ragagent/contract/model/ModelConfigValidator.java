package com.ragagent.contract.model;

/**
 * 模型配置结构校验：必填字段、字段组合与 URL 形态。
 *
 * <p>只做同步结构校验，<b>不发起网络请求</b>；凭据有效性与模型可用性属于"测试连接"，
 * 不在这里判断。校验失败抛 {@link com.ragagent.contract.error.BizException}（400）。</p>
 */
public interface ModelConfigValidator {

    /**
     * 校验一份模型配置；无厂商规则时（空 provider / 本地端点）直接返回。
     *
     * @throws com.ragagent.contract.error.BizException 结构不合法
     */
    void validate(ModelConfigDraft draft);
}
