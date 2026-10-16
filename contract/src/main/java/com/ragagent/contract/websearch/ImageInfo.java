package com.ragagent.contract.websearch;

/**
 * 图片富化信息。
 *
 * <p>检索/富化链路的共享形状（上提契约面）。JSON 键为 snake_case，六个键恒输出
 * （不做空值省略）；会落 jsonb（image_info 列经各 TypeHandler 透传）——
 * 线格式注解由 {@code platform.web.ContractJsonMixinsWebSearch} 的 mixin 承载（契约面纯 JDK）。</p>
 */
public class ImageInfo {

    private String url = "";

    private String originalUrl = "";

    private int startPos;

    private int endPos;

    private String caption = "";

    private String ocrText = "";

    public String getUrl() { return url == null ? "" : url; }
    public void setUrl(String v) { url = v == null ? "" : v; }
    public String getOriginalUrl() { return originalUrl == null ? "" : originalUrl; }
    public void setOriginalUrl(String v) { originalUrl = v == null ? "" : v; }
    public int getStartPos() { return startPos; }
    public void setStartPos(int v) { startPos = v; }
    public int getEndPos() { return endPos; }
    public void setEndPos(int v) { endPos = v; }
    public String getCaption() { return caption == null ? "" : caption; }
    public void setCaption(String v) { caption = v == null ? "" : v; }
    public String getOcrText() { return ocrText == null ? "" : ocrText; }
    public void setOcrText(String v) { ocrText = v == null ? "" : v; }
}
