package com.ragagent.platform.web;

/**
 * 请求体解析错误的文案与判定（Jackson 版本耦合点集中处）。
 *
 * <p><b>文案是线上行为</b>：空 body 的 400 文案被契约金片逐字节钉住
 * （{@code *-empty-body.json}），改字面等于改线上响应。</p>
 *
 * <p><b>为什么集中</b>：此前 40 处调用点各自写 Jackson 原文，一旦 Jackson 升级/更名，
 * 行为会静默漂移且只能靠金片发现；判定与文案收敛到本类后，升级只需改一处。</p>
 */
public final class JsonBodyErrors {

    /** 空请求体的错误文案（锁定值，勿改字面）。 */
    public static final String EMPTY_BODY = "No content to map due to end-of-input";

    private JsonBodyErrors() {
    }

    /**
     * 该异常是否来自"空输入"（Jackson 的 {@code MismatchedInputException} 消息形态）。
     *
     * <p>按消息判定而非异常类型：空 body 走 {@code readValue} 时抛出的是
     * {@code MismatchedInputException}，与"JSON 语法错误"同型，只能靠消息区分。</p>
     */
    public static boolean isEmptyInput(Throwable error) {
        if (error == null) {
            return false;
        }
        String message = error.getMessage();
        return message != null && message.contains(EMPTY_BODY);
    }
}
