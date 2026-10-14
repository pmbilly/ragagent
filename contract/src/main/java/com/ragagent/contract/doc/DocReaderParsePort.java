package com.ragagent.contract.doc;

import java.util.List;

/**
 * 文档解析服务（DocReader）的解析端口：把文件字节解析为 markdown 正文 + 内联图片。
 *
 * <p><b>为什么需要它</b>：im 的附件准备、session 的临时文档解析、initialization 的连通性
 * 探测此前都直连知识域的 {@code knowledge.client.DocReaderClient}（域实现 ✗）。本端口与
 * 管理面 {@link DocReaderAdminPort} 分列：解析面给"内容管线"用，管理面给系统管理页用。</p>
 *
 * <p>失败语义与迁移前一致：解析失败以异常表达（受检），调用方按各自语义降级
 * （im 记 WARN 返回 null；session 落 markFailed）。</p>
 */
public interface DocReaderParsePort {

    /**
     * 解析文件。
     *
     * @param fileContent  文件字节
     * @param fileName     文件名
     * @param fileType     文件类型（去点的扩展名，如 {@code pdf}）
     * @param title        标题（可空）
     * @param parserEngine 引擎名（空串 = 服务端默认路由；可空）
     * @return 解析结果；实现返回 {@code null} 时调用方按"无结果"处理
     */
    ParsedDocumentView parse(byte[] fileContent, String fileName, String fileType,
                             String title, String parserEngine) throws Exception;

    /** 解析结果视图（markdown 正文 + 内联图片 + 图片计数）。 */
    record ParsedDocumentView(String markdown, int imageCount, List<ImageRefView> imageRefs) {

        /** 无内联图片的便捷构造（与客户端记录的便捷形态一致）。 */
        public ParsedDocumentView(String markdown, int imageCount) {
            this(markdown, imageCount, List.of());
        }
    }

    /** 内联图片视图（解析器直出，字节尚未落盘）。 */
    record ImageRefView(String filename, String originalRef, String mimeType, byte[] imageData) {
    }
}
