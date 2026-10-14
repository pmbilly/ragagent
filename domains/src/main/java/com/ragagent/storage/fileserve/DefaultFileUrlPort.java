package com.ragagent.storage.fileserve;

import java.io.IOException;

import com.ragagent.common.storage.support.FileUrlPort;

/**
 * {@link FileUrlPort} 的进程级默认实现：把 fileserve 读取面（{@link FileContentService}）
 * 适配成"只有 {@code GetFileURL}"的窄口；失败按端口契约转 {@link RuntimeException}。
 *
 * <p><b>为何是顶层类</b>：A20 守卫要求 L1 契约端口在域内有且仅有一个可检出的顶层实现
 * （此前它是 {@code StorageUrlWiringConfig} 的私有 record，端口上移 L1 后会显示"契约悬空"）。</p>
 */
public record DefaultFileUrlPort(FileContentService inner) implements FileUrlPort {

    @Override
    public String getFileURL(String filePath) {
        try {
            return inner.getFileURL(filePath);
        } catch (IOException e) {
            throw new IllegalStateException(
                    e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
    }
}
