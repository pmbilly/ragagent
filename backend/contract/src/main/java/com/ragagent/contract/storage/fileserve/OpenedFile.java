package com.ragagent.contract.storage.fileserve;

import com.ragagent.contract.storage.provider.SeekableSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 已打开的存储对象，三形态：
 * <ul>
 * <li>{@code seekable}：本地盘路径，可 seek → 随机读（Range）支路；</li>
 * <li>{@code stream}：只能顺序读的流（云对象）→ 流式支路，**不缓冲整个对象**；</li>
 * <li>{@code bytes}：已在内存的字节 → 流式支路。</li>
 * </ul>
 *
 * <p>自 起上提契约（原 {@code platform.storage.fileserve.FileTransport} 的嵌套 record）；
 * {@code FileTransport} 保留 HTTP 流式写出面并引用本类。</p>
 */
public record OpenedFile(SeekableSource seekable, byte[] bytes, InputStream stream, long size) {

    public static OpenedFile ofSeekable(Path path, long size) {
        return new OpenedFile(new PathSeekableSource(path), null, null, size);
    }

    /**
     * provider 侧的可随机读源（如 minio 形态）：{@code size} 由调用方先取（HEAD）
     * 传入，避免 ServeContent 再做一次长度探测。
     */
    public static OpenedFile ofSeekableSource(SeekableSource source, long size) {
        return new OpenedFile(source, null, null, size);
    }

    public static OpenedFile ofBytes(byte[] data) {
        return new OpenedFile(null, data, null, data.length);
    }

    /** 只能顺序读的流（云对象 body）；{@code size} 未知时传 0。 */
    public static OpenedFile ofStream(InputStream stream, long size) {
        return new OpenedFile(null, null, stream, size);
    }

    /** 读全量字节（三形态通吃；流形态读完即关）。 */
    public byte[] readAllBytes() throws IOException {
        if (bytes != null) {
            return bytes;
        }
        if (stream != null) {
            try (InputStream in = stream) {
                return in.readAllBytes();
            }
        }
        if (seekable != null) {
            try (InputStream in = seekable.open(0)) {
                return in.readAllBytes();
            }
        }
        return new byte[0];
    }

    /** 本地盘的可随机读源：{@code open(offset)} 用 skipNBytes 定位。 */
    private record PathSeekableSource(Path path) implements SeekableSource {

        @Override
        public long size() throws IOException {
            return Files.size(path);
        }

        @Override
        public InputStream open(long offset) throws IOException {
            InputStream in = Files.newInputStream(path);
            try {
                in.skipNBytes(offset);
            } catch (IOException | RuntimeException e) {
                in.close();
                throw e;
            }
            return in;
        }
    }
}
