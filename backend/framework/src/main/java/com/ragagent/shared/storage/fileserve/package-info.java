/**
 * 存储 · 文件服务（framework 侧）：可打开的读取载体与下载传输。
 *
 * <p>业务面的读写端口（{@code WritableFileContentService} 等）已归 {@code app/storage/api}；
 * 本包只剩 {@code OpenedFile} / {@code SeekableSource} 这类与 HTTP 传输绑定的技术载体。</p>
 */
package com.ragagent.shared.storage.fileserve;
