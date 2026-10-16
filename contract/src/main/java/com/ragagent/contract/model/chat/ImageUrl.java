package com.ragagent.contract.model.chat;

/**
 * 图片 URL 结构。
 * JSON 字段序 = 声明序：url 恒输出，detail 为空时省略——
 * 线格式注解由 {@code platform.web.ContractJsonMixinsModelChat} 的 mixin 承载（契约面纯 JDK）。
 */
public class ImageUrl {

    /** URL 或 base64 data URI */
    private String url = "";
    /** "auto" / "low" / "high" */
    private String detail;

    public ImageUrl() {
    }

    public ImageUrl(String url, String detail) {
        this.url = url == null ? "" : url;
        this.detail = detail;
    }

    public String getUrl() { return url; }
    public void setUrl(String v) { url = v == null ? "" : v; }
    public String getDetail() { return detail; }
    public void setDetail(String v) { detail = v; }
}
