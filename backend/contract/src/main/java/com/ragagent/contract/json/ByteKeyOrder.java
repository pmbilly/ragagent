package com.ragagent.contract.json;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;

/**
 * JSON 对象键的字节序比较器：逐 UTF-8 字节比较，短者在前。
 *
 * <p>"map 键排序"政策的单一实现（原在 {@code platform.web.SortedMapSerializer#KEY_BYTE_ORDER}，
 * 上提至契约面以求契约/序列化器共用）：{@code SortedMapSerializer.KEY_BYTE_ORDER} 与
 * {@code SearchResult} 的 metadata 归一化都引用本常量。</p>
 */
public final class ByteKeyOrder {

    public static final Comparator<String> COMPARATOR = (a, b) -> {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        int shared = Math.min(x.length, y.length);
        for (int i = 0; i < shared; i++) {
            int diff = (x[i] & 0xff) - (y[i] & 0xff);
            if (diff != 0) {
                return diff;
            }
        }
        return x.length - y.length;
    };

    private ByteKeyOrder() {
    }
}
