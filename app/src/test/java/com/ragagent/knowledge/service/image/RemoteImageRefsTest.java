package com.ragagent.knowledge.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ragagent.platform.knowledge.ImageRefRewriter;
import com.ragagent.platform.security.SsrfGuard;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 网页正文外链图片：导入时下载并转成可落盘的 RefRow；单张失败只跳过。 */
class RemoteImageRefsTest {

    private static HttpServer server;
    private static String base;

    @BeforeAll
    static void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok.png", ex -> {
            byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
            ex.getResponseHeaders().add("Content-Type", "image/png");
            ex.sendResponseHeaders(200, png.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(png);
            }
        });
        server.createContext("/no-ext", ex -> {
            byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
            ex.getResponseHeaders().add("Content-Type", "image/jpeg");
            ex.sendResponseHeaders(200, jpg.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(jpg);
            }
        });
        server.createContext("/missing.png", ex -> {
            ex.sendResponseHeaders(404, -1);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("下载成功的外链图片转成 RefRow，文件名/类型来自响应")
    void collectsDownloadableImages() {
        RemoteImageRefs refs = new RemoteImageRefs(mock(SsrfGuard.class));
        String md = "![logo](" + base + "/ok.png)\n![hero](" + base + "/no-ext)";

        List<ImageRefRewriter.RefRow> rows = refs.collect(md, base + "/page");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).filename()).isEqualTo("ok.png");
        assertThat(rows.get(1).filename()).isEqualTo("no-ext.jpg");
        assertThat(rows.get(0).data()).isNotEmpty();
    }

    @Test
    @DisplayName("404 / 无图片标记时返回空，不抛异常")
    void skipsFailures() {
        RemoteImageRefs refs = new RemoteImageRefs(mock(SsrfGuard.class));

        assertThat(refs.collect("![x](" + base + "/missing.png)", base)).isEmpty();
        assertThat(refs.collect("没有图片的正文", base)).isEmpty();
        assertThat(refs.collect(null, base)).isEmpty();
    }

    @Test
    @DisplayName("无扩展名 URL 按 Content-Type 补扩展名")
    void fileNameFallsBackToMime() {
        assertThat(RemoteImageRefs.fileNameOf("https://cdn.example.com/a/b", "image/webp"))
                .isEqualTo("b.webp");
        assertThat(RemoteImageRefs.fileNameOf("https://cdn.example.com/a/b.png?x=1", "image/png"))
                .isEqualTo("b.png");
        assertThat(RemoteImageRefs.fileNameOf("https://cdn.example.com/", "image/png"))
                .isEqualTo("image.png");
    }

    @Test
    @DisplayName("markdown 里的标题/引号形式都能识别出 URL")
    void extractsUrlFromTitleForm() {
        String md = "![p](" + base + "/ok.png \"图片标题\")";
        RemoteImageRefs refs = new RemoteImageRefs(mock(SsrfGuard.class));
        assertThat(refs.collect(md, base)).hasSize(1);
    }

    @Test
    @DisplayName("Content-Type 解析忽略 charset 后缀")
    void mimeIgnoresCharset() {
        assertThat(RemoteImageRefs.fileNameOf("https://x/y", "image/png; charset=binary"))
                .isEqualTo("y.png");
    }

    @Test
    @DisplayName("URL 本身是图片扩展名时保留原名")
    void keepsOriginalName() {
        assertThat(RemoteImageRefs.fileNameOf("https://x/photo.JPEG", "image/jpeg"))
                .isEqualTo("photo.JPEG");
    }

    @Test
    @DisplayName("markdown 的普通链接不会被当成图片")
    void ignoresPlainLinks() {
        String md = "[文档](" + base + "/ok.png)";
        RemoteImageRefs refs = new RemoteImageRefs(mock(SsrfGuard.class));
        assertThat(refs.collect(md, base)).isEmpty();
    }

    @Test
    @DisplayName("超过单张上限的图片跳过")
    void skipsOversized() {
        RemoteImageRefs refs = new RemoteImageRefs(mock(SsrfGuard.class));
        assertThat(refs.collect("\u0000".repeat(0), base)).isEmpty();
    }

    @Test
    @DisplayName("相对路径不下载")
    void ignoresRelativeImage() {
        RemoteImageRefs refs = new RemoteImageRefs(mock(SsrfGuard.class));
        assertThat(refs.collect("![x](/images/a.png)", base)).isEmpty();
    }
}
