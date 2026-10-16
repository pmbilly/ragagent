package com.ragagent.knowledge.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ragagent.contract.storage.ResourceCatalogPort;
import com.ragagent.contract.storage.fileserve.GlobalFileServicePort;
import com.ragagent.contract.storage.fileserve.OpenedFile;
import com.ragagent.contract.storage.fileserve.WritableFileContentService;
import com.ragagent.knowledge.client.DocReaderClient;
import com.ragagent.knowledge.domain.Knowledge;

/**
 * 解析直出图片的落盘 + 绑定 + 正文改写：图片必须落在 exports 区并绑定到本篇知识，
 * 知识库文件代理才会放行（exports 命名空间 + 归属绑定双条件）。
 */
class ExtractedImageStoreTest {

    private final List<String> savedNames = new ArrayList<>();
    private final List<String> bound = new ArrayList<>();
    private int handleSeq;

    private final GlobalFileServicePort fileService = () -> new WritableFileContentService() {
        @Override
        public String saveBytes(byte[] data, long tenantId, String fileName, boolean temp) {
            savedNames.add(fileName);
            return "resource://h" + (++handleSeq);
        }

        @Override
        public void deleteFile(String filePath) {
        }

        @Override
        public OpenedFile getFile(String filePath) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public String getFileURL(String filePath) {
            return "";
        }
    };

    private final ResourceCatalogPort resourceCatalog = new ResourceCatalogPort() {
        @Override
        public Optional<ResourceView> resolve(String reference) {
            return Optional.empty();
        }

        @Override
        public void bind(String reference, String ownerType, String ownerId, String relation) {
            bound.add(ownerType + ":" + ownerId + ":" + relation + ":" + reference);
        }
    };

    private final ExtractedImageStore store = new ExtractedImageStore(fileService, resourceCatalog);

    @Test
    void storesImagesBindsToKnowledgeAndRewritesMarkdown() {
        Knowledge k = knowledge("k1", "pdf");
        DocReaderClient.ImageRef image = new DocReaderClient.ImageRef(
                "p3_img1.jpg", "images/p3_img1.jpg", "image/jpeg", pngBytes(200, 150));
        DocReaderClient.ImageRef icon = new DocReaderClient.ImageRef(
                "icon.png", "images/icon.png", "image/png", pngBytes(48, 48));

        String markdown = store.storeAndRewrite(k,
                List.of(image, icon), "![a](images/p3_img1.jpg)\n![icon](images/icon.png)");

        // 命中图改写为资源引用；小图标（两轴 < 64）过滤并保留原引用
        assertThat(markdown).isEqualTo("![a](resource://h1)\n![icon](images/icon.png)");
        assertThat(savedNames).hasSize(1);
        assertThat(savedNames.get(0)).endsWith(".jpg");
        assertThat(bound).containsExactly("knowledge:k1:attachment:resource://h1");
    }

    @Test
    void imageSourceDocumentKeepsSmallImages() {
        Knowledge k = knowledge("k2", "png");
        DocReaderClient.ImageRef small = new DocReaderClient.ImageRef(
                "tiny.png", "images/tiny.png", "image/png", pngBytes(32, 32));

        String markdown = store.storeAndRewrite(k, List.of(small), "![tiny](images/tiny.png)");

        assertThat(markdown).isEqualTo("![tiny](resource://h1)");
        assertThat(bound).containsExactly("knowledge:k2:attachment:resource://h1");
    }

    @Test
    void noImageRefsLeaveMarkdownUntouched() {
        assertThat(store.storeAndRewrite(knowledge("k3", "pdf"), List.of(), "# 标题"))
                .isEqualTo("# 标题");
        assertThat(store.storeAndRewrite(knowledge("k3", "pdf"), null, "# 标题"))
                .isEqualTo("# 标题");
        assertThat(savedNames).isEmpty();
        assertThat(bound).isEmpty();
    }

    @Test
    void missingTenantSkipsStoring() {
        Knowledge k = knowledge("k4", "pdf");
        k.setTenantId(null);
        DocReaderClient.ImageRef image = new DocReaderClient.ImageRef(
                "a.png", "images/a.png", "image/png", pngBytes(200, 150));

        assertThat(store.storeAndRewrite(k, List.of(image), "![a](images/a.png)"))
                .isEqualTo("![a](images/a.png)");
        assertThat(savedNames).isEmpty();
    }

    @Test
    void unreferencedImagesAreNotStored() {
        Knowledge k = knowledge("k5", "pdf");
        DocReaderClient.ImageRef image = new DocReaderClient.ImageRef(
                "unused.png", "images/unused.png", "image/png", pngBytes(200, 150));

        assertThat(store.storeAndRewrite(k, List.of(image), "正文没有图片引用"))
                .isEqualTo("正文没有图片引用");
        assertThat(savedNames).isEmpty();
        assertThat(bound).isEmpty();
    }

    private static Knowledge knowledge(String id, String fileType) {
        Knowledge k = new Knowledge();
        k.setId(id);
        k.setTenantId(1L);
        k.setFileType(fileType);
        return k;
    }

    /** 生成可解码的 PNG（图标过滤需要真实尺寸）。 */
    private static byte[] pngBytes(int width, int height) {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
