package com.ragagent.contract.model;

/**
 * 单条重排结果（重排端口的返回形状）。
 *
 * <p><b>宽容解析</b>：{@code document} 可以是字符串也可以是 {@code {"text":...}} 对象；
 * 分数字段先看 {@code relevance_score}，缺失回落 {@code score}；两者都没有时为 0。
 * 解析/序列化在实现侧的 {@code rerank.RankResultCodec}（本类持纯形状，不解 JSON）。</p>
 *
 * <p>序列化形如 {@code {"index":N,"document":{"text":"..."},"relevance_score":X}}
 * （按字段声明序，document 恒输出对象）——键名由 {@code platform.web.ContractJsonMixinsModel}
 * 的 mixin 承载。</p>
 */
public final class RankResult {

    private int index;
    private final DocumentInfo document = new DocumentInfo();
    private double relevanceScore;

    public int getIndex() {
        return index;
    }

    public void setIndex(int v) {
        index = v;
    }

    public DocumentInfo getDocument() {
        return document;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double v) {
        relevanceScore = v;
    }

    /** 文档信息；自身按 {@code {"text":"..."}} 序列化。 */
    public static final class DocumentInfo {
        private String text = "";

        public String getText() {
            return text;
        }

        public void setText(String v) {
            text = v == null ? "" : v;
        }
    }
}
