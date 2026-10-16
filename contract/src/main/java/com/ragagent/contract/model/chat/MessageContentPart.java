package com.ragagent.contract.model.chat;

/**
 * 多内容消息的一部分。
 * JSON 字段序 = 声明序：type 恒输出，text/image_url 为空时省略——
 * 线格式注解由 {@code platform.web.ContractJsonMixinsModelChat} 的 mixin 承载（契约面纯 JDK）。
 */
public class MessageContentPart {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_IMAGE_URL = "image_url";

    /** "text" 或 "image_url" */
    private String type = "";
    private String text;
    private ImageUrl imageUrl;

    public MessageContentPart() {
    }

    public static MessageContentPart text(String text) {
        MessageContentPart p = new MessageContentPart();
        p.type = TYPE_TEXT;
        p.text = text;
        return p;
    }

    public static MessageContentPart image(String url, String detail) {
        MessageContentPart p = new MessageContentPart();
        p.type = TYPE_IMAGE_URL;
        p.imageUrl = new ImageUrl(url, detail);
        return p;
    }

    public String getType() { return type; }
    public void setType(String v) { type = v == null ? "" : v; }
    public String getText() { return text; }
    public void setText(String v) { text = v; }
    public ImageUrl getImageUrl() { return imageUrl; }
    public void setImageUrl(ImageUrl v) { imageUrl = v; }
}
