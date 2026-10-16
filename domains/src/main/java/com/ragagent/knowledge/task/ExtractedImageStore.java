package com.ragagent.knowledge.task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ragagent.contract.storage.ResourceCatalogPort;
import com.ragagent.contract.storage.fileserve.GlobalFileServicePort;
import com.ragagent.knowledge.client.DocReaderClient;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.platform.knowledge.ImageRefRewriter;
import com.ragagent.platform.text.ContentTypeByFilename;

/**
 * 知识库解析直出图片的落盘与绑定：字节写租户 {@code exports} 区（进程级文件服务顺带注册资源），
 * 再绑定 {@code knowledge} 属主——知识库文件代理要求「exports 命名空间 + 绑定到本篇知识」
 * 双条件，缺一则取图 403。
 *
 * <p>单张图片落盘/绑定失败只告警并保留原引用，不阻断解析；未被正文引用的图片不落盘。</p>
 */
@Component
public class ExtractedImageStore {

    private static final Logger log = LoggerFactory.getLogger(ExtractedImageStore.class);

    private final GlobalFileServicePort fileService;
    private final ResourceCatalogPort resourceCatalog;

    public ExtractedImageStore(GlobalFileServicePort fileService, ResourceCatalogPort resourceCatalog) {
        this.fileService = fileService;
        this.resourceCatalog = resourceCatalog;
    }

    /** 落盘 + 绑定 + 改写正文引用；返回改写后的 markdown。 */
    public String storeAndRewrite(Knowledge k, List<DocReaderClient.ImageRef> refs, String markdown) {
        if (refs == null || refs.isEmpty()) {
            return markdown;
        }
        long tenantId = k.getTenantId() == null ? 0L : k.getTenantId();
        if (tenantId == 0) {
            log.warn("skip storing extracted images: knowledge {} has no tenant", k.getId());
            return markdown;
        }
        List<ImageRefRewriter.RefRow> rows = new ArrayList<>(refs.size());
        for (DocReaderClient.ImageRef ref : refs) {
            rows.add(new ImageRefRewriter.RefRow(ref.originalRef(), ref.filename(),
                    ref.mimeType(), ref.imageData()));
        }
        // 来源文件本身是图片格式时保留原图（不做小图标过滤）
        boolean filterIcons = !ContentTypeByFilename.isImageExt(k.getFileType());
        ImageRefRewriter.Result result = ImageRefRewriter.rewrite(markdown, rows, filterIcons,
                (data, fileName) -> {
                    String stored;
                    try {
                        stored = fileService.globalService().saveBytes(data, tenantId, fileName, false);
                    } catch (IOException e) {
                        throw new IllegalStateException("save extracted image failed: " + e.getMessage(), e);
                    }
                    resourceCatalog.bind(stored, "knowledge", k.getId(), "attachment");
                    return stored;
                });
        if (!result.stored().isEmpty()) {
            log.info("[KnowledgeProcess] stored {} extracted images for knowledge {}",
                    result.stored().size(), k.getId());
        }
        return result.markdown();
    }
}
