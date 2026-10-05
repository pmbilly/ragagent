package com.ragagent.im.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.ragagent.im.runtime.AdapterInterfaces;
import com.ragagent.im.runtime.CallbackExchange;
import com.ragagent.im.runtime.ImTypes;
import com.ragagent.im.runtime.IncomingMessage;
import com.ragagent.im.runtime.ReplyMessage;
import com.ragagent.knowledge.client.DocReaderClient;

/**
 * IM 附件解析（对齐 Go prepareIMAttachments 的语义面）：
 * 类型/大小/扩展名门禁、文本直读与截断、图片按实际 MIME 出 data URI、
 * docreader 可用时的解析路径。
 */
class ImAttachmentPreparerTest {

    /** 可下载的假适配器。 */
    private static final class DownloadingAdapter
            implements AdapterInterfaces.Adapter, AdapterInterfaces.FileDownloader {
        byte[] content = new byte[0];
        String fileName = "";

        @Override
        public String platform() {
            return "fake";
        }

        @Override
        public Exception verifyCallback(CallbackExchange exchange) {
            return null;
        }

        @Override
        public IncomingMessage parseCallback(CallbackExchange exchange) {
            return null;
        }

        @Override
        public void sendReply(IncomingMessage incoming, ReplyMessage reply) {
        }

        @Override
        public boolean handleURLVerification(CallbackExchange exchange) {
            return false;
        }

        @Override
        public DownloadedFile downloadFile(IncomingMessage msg) {
            return new DownloadedFile(content, fileName);
        }
    }

    /** 不支持下载的假适配器。 */
    private static final class PlainAdapter implements AdapterInterfaces.Adapter {
        @Override
        public String platform() {
            return "fake";
        }

        @Override
        public Exception verifyCallback(CallbackExchange exchange) {
            return null;
        }

        @Override
        public IncomingMessage parseCallback(CallbackExchange exchange) {
            return null;
        }

        @Override
        public void sendReply(IncomingMessage incoming, ReplyMessage reply) {
        }

        @Override
        public boolean handleURLVerification(CallbackExchange exchange) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DocReaderClient> providerOf(DocReaderClient client) {
        ObjectProvider<DocReaderClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    private static IncomingMessage message(String type, String fileName, long size) {
        IncomingMessage msg = IncomingMessage.of("fake", "u1", "m1");
        msg.messageType = type;
        msg.fileName = fileName;
        msg.fileSize = size;
        return msg;
    }

    @Test
    void nonAttachmentMessageReturnsEmpty() throws Exception {
        ImAttachmentPreparer preparer = new ImAttachmentPreparer(providerOf(null));
        ImAttachmentPreparer.Prepared prepared = preparer.prepare(
                message(ImTypes.MESSAGE_TYPE_TEXT, "x.txt", 1), new DownloadingAdapter());
        assertTrue(prepared.attachments().isEmpty());
        assertTrue(prepared.imageUrls().isEmpty());
    }

    @Test
    void oversizedAttachmentIsRejectedByReportedSize() {
        ImAttachmentPreparer preparer = new ImAttachmentPreparer(providerOf(null));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> preparer.prepare(
                        message(ImTypes.MESSAGE_TYPE_FILE, "big.pdf",
                                ImAttachmentPreparer.MAX_ATTACHMENT_BYTES + 1),
                        new DownloadingAdapter()));
        assertTrue(e.getMessage().contains("exceeds the 32 MiB limit"));
    }

    @Test
    void adapterWithoutDownloaderIsRejected() {
        ImAttachmentPreparer preparer = new ImAttachmentPreparer(providerOf(null));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> preparer.prepare(message(ImTypes.MESSAGE_TYPE_FILE, "a.pdf", 10),
                        new PlainAdapter()));
        assertTrue(e.getMessage().contains("does not support attachment download"));
    }

    @Test
    void missingExtensionIsRejected() {
        ImAttachmentPreparer preparer = new ImAttachmentPreparer(providerOf(null));
        DownloadingAdapter adapter = new DownloadingAdapter();
        adapter.fileName = "noext";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> preparer.prepare(message(ImTypes.MESSAGE_TYPE_FILE, "noext", 3), adapter));
        assertTrue(e.getMessage().contains("no file extension"));
    }

    @Test
    void textAttachmentIsReadVerbatimWhenSmall() throws Exception {
        ImAttachmentPreparer preparer = new ImAttachmentPreparer(providerOf(null));
        DownloadingAdapter adapter = new DownloadingAdapter();
        adapter.fileName = "notes.txt";
        adapter.content = "第一行\n第二行".getBytes(StandardCharsets.UTF_8);

        ImAttachmentPreparer.Prepared prepared = preparer.prepare(
                message(ImTypes.MESSAGE_TYPE_FILE, "notes.txt", 10), adapter);
        assertEquals(1, prepared.attachments().size());
        var attachment = prepared.attachments().get(0);
        assertEquals("notes.txt", attachment.getFileName());
        assertEquals(".txt", attachment.getFileType());
        assertEquals("第一行\n第二行", attachment.getContent());
        assertEquals(2, attachment.getLineCount());
        assertFalse(attachment.isTruncated());
        assertTrue(prepared.imageUrls().isEmpty());
        // 原始字节随产物返回（渠道配了 KB 时异步入库用）
        assertEquals("notes.txt", prepared.raw().fileName());
        assertEquals(adapter.content.length, prepared.raw().content().length);
    }

    @Test
    void textAttachmentIsTruncatedAtLineLimit() throws Exception {
        ImAttachmentPreparer preparer = new ImAttachmentPreparer(providerOf(null));
        DownloadingAdapter adapter = new DownloadingAdapter();
        adapter.fileName = "many.log";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append("line-").append(i);
        }
        adapter.content = sb.toString().getBytes(StandardCharsets.UTF_8);

        var attachment = preparer.prepare(
                message(ImTypes.MESSAGE_TYPE_FILE, "many.log", 1), adapter).attachments().get(0);
        assertEquals(600, attachment.getLineCount(), "行数按原文计");
        assertTrue(attachment.isTruncated());
        assertEquals(ImAttachmentPreparer.MAX_CONTENT_LINES,
                attachment.getContent().split("\n", -1).length, "正文截到 500 行");
    }

    @Test
    void imageUsesDetectedMimeRegardlessOfExtension() throws Exception {
        ImAttachmentPreparer preparer = new ImAttachmentPreparer(providerOf(null));
        DownloadingAdapter adapter = new DownloadingAdapter();
        // 平台把 JPEG 命名为 .png：data URI 必须用实际内容类型（对齐 Go 的用例）
        adapter.fileName = "platform-image.png";
        adapter.content = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 'J', 'F', 'I', 'F', 0x00, 0x01};

        ImAttachmentPreparer.Prepared prepared = preparer.prepare(
                message(ImTypes.MESSAGE_TYPE_IMAGE, "platform-image.png", 12), adapter);
        assertEquals(1, prepared.imageUrls().size());
        assertTrue(prepared.imageUrls().get(0).startsWith("data:image/jpeg;base64,"));
        assertEquals("platform-image.png", prepared.attachments().get(0).getFileName());
    }

    @Test
    void oversizedImageSkipsVisionButKeepsAttachment() throws Exception {
        ImAttachmentPreparer preparer = new ImAttachmentPreparer(providerOf(null));
        DownloadingAdapter adapter = new DownloadingAdapter();
        adapter.fileName = "huge.png";
        byte[] png = new byte[(int) ImAttachmentPreparer.MAX_VISION_BYTES + 1];
        png[0] = (byte) 0x89;
        png[1] = 'P';
        png[2] = 'N';
        png[3] = 'G';
        adapter.content = png;

        ImAttachmentPreparer.Prepared prepared = preparer.prepare(
                message(ImTypes.MESSAGE_TYPE_IMAGE, "huge.png", png.length), adapter);
        assertTrue(prepared.imageUrls().isEmpty(), "超 8MiB 不进 vision");
        assertEquals(1, prepared.attachments().size());
    }

    @Test
    void documentGoesThroughDocReaderWhenAvailable() throws Exception {
        DocReaderClient client = mock(DocReaderClient.class);
        when(client.read(any(), any(), any(), any(), any()))
                .thenReturn(new DocReaderClient.ParseResult("# parsed body", 0));
        ImAttachmentPreparer preparer = new ImAttachmentPreparer(providerOf(client));

        DownloadingAdapter adapter = new DownloadingAdapter();
        adapter.fileName = "report.pdf";
        adapter.content = new byte[]{1, 2, 3};

        var attachment = preparer.prepare(
                message(ImTypes.MESSAGE_TYPE_FILE, "report.pdf", 3), adapter).attachments().get(0);
        assertEquals("# parsed body", attachment.getContent());
        assertFalse(attachment.isTruncated());
    }

    @Test
    void truncateUtf8ByBytesKeepsCharBoundary() {
        // 两个汉字 = 6 字节；截到 4 字节必须回退到 3 字节（保留第一个汉字）
        String text = "中文";
        assertEquals("中", ImAttachmentPreparer.truncateUtf8ByBytes(text, 4));
        assertEquals(text, ImAttachmentPreparer.truncateUtf8ByBytes(text, 6));
    }

    @Test
    void extensionIsLowerCaseWithoutDot() {
        assertEquals("pdf", ImAttachmentPreparer.extensionOf("A.PDF"));
        assertEquals("gz", ImAttachmentPreparer.extensionOf("a.tar.gz"));
        assertEquals("", ImAttachmentPreparer.extensionOf("noext"));
        assertEquals("", ImAttachmentPreparer.extensionOf(null));
    }
}
