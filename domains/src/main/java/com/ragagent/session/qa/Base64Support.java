package com.ragagent.session.qa;

import java.util.Base64;

/** 附件 data URL 解码（原为 {@code KnowledgeQaController} 的内嵌类）。 */
public final class Base64Support {

    private Base64Support() {
    }

    public static byte[] decode(String data) {
        String payload = data == null ? "" : data;
        int comma = payload.indexOf(',');
        if (payload.startsWith("data:") && comma > 0) {
            payload = payload.substring(comma + 1);
        }
        return Base64.getDecoder().decode(payload);
    }
}
