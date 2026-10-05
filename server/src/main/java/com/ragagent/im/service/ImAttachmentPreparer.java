package com.ragagent.im.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import com.ragagent.im.runtime.AdapterInterfaces;
import com.ragagent.im.runtime.AdapterInterfaces.Adapter;
import com.ragagent.im.runtime.ImTypes;
import com.ragagent.im.runtime.IncomingMessage;
import com.ragagent.knowledge.client.DocReaderClient;
import com.ragagent.session.domain.MessageAttachment;
import com.ragagent.session.service.QaSupport;

/**
 * IM 文件/图片附件的下载与解析（对齐 Go internal/im 的 prepareIMAttachments）。
 *
 * <ul>
 *   <li>附件上限 32 MiB（平台上报大小与实际字节各拦一次）；图片直发 vision 上限 8 MiB；</li>
 *   <li>图片先试 docreader OCR（失败继续——有 vision 直发兜底）；文本类扩展名直接
 *       UTF-8 读；其余格式走 docreader（失败只 WARN，保留附件元数据）；</li>
 *   <li>解析文本按 32 KiB / 500 行截断（UTF-8 边界对齐）；</li>
 *   <li>图片按实际内容识别 MIME（不信任扩展名）后转 base64 data URI。</li>
 * </ul>
 */
final class ImAttachmentPreparer {

    private static final Logger log = LoggerFactory.getLogger(ImAttachmentPreparer.class);

    /** 附件上限（Go {@code maxIMAttachmentBytes}）。 */
    static final long MAX_ATTACHMENT_BYTES = 32L << 20;
    /** 图片直发 vision 的上限（Go {@code maxIMVisionAttachmentBytes}）。 */
    static final long MAX_VISION_BYTES = 8L << 20;
    /** 解析文本的行数上限（Go {@code maxIMAttachmentLines}）。 */
    static final int MAX_CONTENT_LINES = 500;
    /** 解析文本的字节上限（Go {@code maxIMAttachmentContentBytes}）。 */
    static final int MAX_CONTENT_BYTES = 32 << 10;
    /** 文本类扩展名（不带点；直接 UTF-8 读，不经 docreader）。 */
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "md", "markdown", "txt", "csv", "json", "xml", "yaml", "yml", "log");
    /** 图片扩展名（不带点）。 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp");

    private final ObjectProvider<DocReaderClient> docReaders;

    ImAttachmentPreparer(ObjectProvider<DocReaderClient> docReaders) {
        this.docReaders = docReaders;
    }

    /** 解析产物：给 QA 管线的附件列表 + vision 图片 URL（data URI）。 */
    record Prepared(List<MessageAttachment> attachments, List<String> imageUrls) {
        static Prepared empty() {
            return new Prepared(List.of(), List.of());
        }
    }

    /** 把解析产物挂到 QA 请求（附件元数据/正文 + vision 图片）。 */
    static void applyTo(QaSupport.QaRequest qaReq, Prepared prepared) {
        if (qaReq == null || prepared == null) {
            return;
        }
        qaReq.attachments = prepared.attachments();
        if (!prepared.imageUrls().isEmpty()) {
            qaReq.imageUrls = prepared.imageUrls();
        }
    }

    /** 下载并解析 File/Image 消息；非附件类型返回空。失败抛异常（调用方按固定文案回复）。 */
    Prepared prepare(IncomingMessage msg, Adapter adapter) throws Exception {
        if (!ImTypes.MESSAGE_TYPE_FILE.equals(msg.messageType)
                && !ImTypes.MESSAGE_TYPE_IMAGE.equals(msg.messageType)) {
            return Prepared.empty();
        }
        if (msg.fileSize > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException(
                    "attachment exceeds the " + (MAX_ATTACHMENT_BYTES >> 20) + " MiB limit");
        }
        if (!(adapter instanceof AdapterInterfaces.FileDownloader downloader)) {
            throw new IllegalArgumentException(
                    "platform " + msg.platform + " does not support attachment download");
        }
        AdapterInterfaces.FileDownloader.DownloadedFile file = downloader.downloadFile(msg);
        byte[] content = file.content() == null ? new byte[0] : file.content();
        if (content.length > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException(
                    "attachment exceeds the " + (MAX_ATTACHMENT_BYTES >> 20) + " MiB limit");
        }
        String fileName = file.fileName() == null || file.fileName().isEmpty()
                ? (msg.fileName == null ? "" : msg.fileName)
                : file.fileName();
        boolean imageMessage = ImTypes.MESSAGE_TYPE_IMAGE.equals(msg.messageType);
        if (imageMessage && extensionOf(fileName).isEmpty()) {
            fileName = fileName + ".png";
        }
        String ext = extensionOf(fileName);
        if (ext.isEmpty()) {
            throw new IllegalArgumentException("attachment has no file extension");
        }

        MessageAttachment attachment = new MessageAttachment();
        attachment.setFileName(fileName);
        attachment.setFileType("." + ext);
        attachment.setFileSize(content.length);

        boolean imageFormat = imageMessage || IMAGE_EXTENSIONS.contains(ext);
        String markdown = null;
        if (imageFormat) {
            // 图片先试 OCR/文档解析：失败继续（有 vision 直发兜底）
            markdown = tryReadWithDocReader(content, fileName, ext,
                    "image OCR/document parsing failed, continuing with vision input");
        }
        if (markdown == null && TEXT_EXTENSIONS.contains(ext)) {
            markdown = new String(content, StandardCharsets.UTF_8);
        } else if (markdown == null && !imageFormat) {
            markdown = tryReadWithDocReader(content, fileName, ext,
                    "attachment parsing failed, continuing with attachment metadata");
        }
        if (markdown != null) {
            applyTruncation(markdown, attachment);
        }

        List<String> imageUrls = new ArrayList<>();
        if (imageFormat) {
            if (content.length <= MAX_VISION_BYTES) {
                String mediaType = detectContentType(content);
                if (!mediaType.startsWith("image/")) {
                    throw new IllegalArgumentException("invalid image content type: " + mediaType);
                }
                imageUrls.add("data:" + mediaType + ";base64,"
                        + Base64.getEncoder().encodeToString(content));
            } else {
                log.warn("[IM] image is too large for direct vision input: size={} limit={}",
                        content.length, MAX_VISION_BYTES);
            }
        }
        return new Prepared(List.of(attachment), imageUrls);
    }

    /** docreader 不可用或解析失败时返回 null（只 WARN，不阻断）。 */
    private String tryReadWithDocReader(byte[] content, String fileName, String ext,
            String warnPrefix) {
        DocReaderClient client = docReaders.getIfAvailable();
        if (client == null) {
            return null;
        }
        try {
            DocReaderClient.ParseResult result = client.read(content, fileName, ext, fileName, "");
            return result == null ? null : result.markdown();
        } catch (Exception e) {
            log.warn("[IM] {}: {}", warnPrefix, e.getMessage());
            return null;
        }
    }

    /** 解析文本按 32 KiB / 500 行截断（对齐 Go applyIMAttachmentTruncation）。 */
    static void applyTruncation(String content, MessageAttachment attachment) {
        attachment.setLineCount((int) content.chars().filter(c -> c == '\n').count() + 1);
        String limited = truncateUtf8ByBytes(content, MAX_CONTENT_BYTES);
        String[] lines = limited.split("\n", MAX_CONTENT_LINES + 1);
        if (lines.length > MAX_CONTENT_LINES) {
            limited = String.join("\n", Arrays.copyOf(lines, MAX_CONTENT_LINES));
        }
        attachment.setContent(limited);
        attachment.setTruncated(limited.length() < content.length());
    }

    /** 按字节截断到 UTF-8 字符边界（对齐 Go truncateUTF8ByBytes）。 */
    static String truncateUtf8ByBytes(String content, int maxBytes) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return content;
        }
        int end = maxBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    /** 小写扩展名（不带点）；无扩展名返回空串。 */
    static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 按魔数识别内容类型（对齐 Go {@code http.DetectContentType} 的图片子集；
     * 认不出按 {@code application/octet-stream} 兜底，供错误文案使用）。
     */
    static String detectContentType(byte[] d) {
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "image/png";
        }
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8
                && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F') {
            return "image/gif";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        if (d.length >= 2 && d[0] == 'B' && d[1] == 'M') {
            return "image/bmp";
        }
        return "application/octet-stream";
    }
}
