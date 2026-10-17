package com.ragagent.contract.session;


/**
 * 挂在消息上的图片。
 *
 * <p>两个键都恒输出：没有说明时 {@code caption} 写 {@code null}。</p>
 *
 * <p><b>落库时的空值语义</b>：null 列表写成 {@code []}（不是 SQL NULL），
 * 所以 Java 实体上的这个列表字段**默认是空列表**，
 * 由类型处理器写成 {@code []}。</p>
 */
public class MessageImage {

    private String url = "";

    private String caption;

    public MessageImage() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String v) {
        this.url = v == null ? "" : v;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String v) {
        this.caption = v;
    }
}
