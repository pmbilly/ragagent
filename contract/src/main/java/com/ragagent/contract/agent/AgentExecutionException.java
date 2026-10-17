package com.ragagent.contract.agent;

/**
 * agent 执行失败（引擎失败通道的契约面类型）。
 *
 * <p>为什么需要它：域侧对"引擎执行失败"与"引擎入口之外的失败"有**两级处理**
 * （前者只记日志——error 事件已由引擎在抛出前发出；后者还要补发一份 error 事件
 * ——否则客户端看不到报错）。契约化后异常类型不跨模块暴露，句柄适配器把引擎的
 * {@code AgentEngineException} 包成本类型，让域侧的两级 catch 原样成立。</p>
 *
 * <p><b>message 即契约</b>：逐字来自引擎（error 事件的 {@code error} 字段与日志原文）。</p>
 */
public class AgentExecutionException extends RuntimeException {

    public AgentExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
