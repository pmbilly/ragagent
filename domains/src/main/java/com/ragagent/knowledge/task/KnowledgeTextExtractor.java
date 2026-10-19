package com.ragagent.knowledge.task;

import com.ragagent.knowledge.client.DocReaderClient;
import com.ragagent.knowledge.domain.Knowledge;
import com.ragagent.knowledge.domain.KnowledgeProcessingSpan;
import com.ragagent.knowledge.domain.span.SpanTracker;
import com.ragagent.knowledge.service.image.ExtractedImageStore;
import com.ragagent.knowledge.storage.TenantFileStorage;
import com.ragagent.platform.knowledge.ImageRefRewriter;
import java.util.List;
import java.util.Map;

/**
 * 入库阶段一：取文本。
 *
 * <p>manual 直接读 {@code metadata.content}；文件经 docreader 解析，图片引用落盘并重写 URL，
 * 随后交给图片 OCR 在引用下方补文字。阶段 span 记录文件类型/名称与字符量；失败只收口 span，
 * 异常向上抛给 worker。</p>
 */
final class KnowledgeTextExtractor {

    private final TenantFileStorage fileStorage;
    private final ExtractedImageStore extractedImageStore;
    private final DocReaderClient docReader;
    private final KnowledgeImageOcr imageOcr;
    private final SpanTracker spanTracker;

    KnowledgeTextExtractor(TenantFileStorage fileStorage,
            ExtractedImageStore extractedImageStore,
            DocReaderClient docReader,
            KnowledgeImageOcr imageOcr,
            SpanTracker spanTracker) {
        this.fileStorage = fileStorage;
        this.extractedImageStore = extractedImageStore;
        this.docReader = docReader;
        this.imageOcr = imageOcr;
        this.spanTracker = spanTracker;
    }

    String extract(Knowledge k, int attempt, String knowledgeId) throws Exception {
        if ("manual".equals(k.getType())) {
            String content = k.getMetadata() != null && k.getMetadata().hasNonNull("content")
                    ? k.getMetadata().get("content").asString() : "";
            return imageOcr.enrich(k, content, attempt, knowledgeId);
        }
        SpanTracker.SpanHandle docSpan = beginStageSpan(attempt, knowledgeId,
                KnowledgeProcessingSpan.STAGE_DOC_READER,
                Map.of(
                        "file_type", k.getFileType() == null ? "" : k.getFileType(),
                        "file_name", k.getFileName() == null ? "" : k.getFileName()));
        String markdown;
        try {
            byte[] content = fileStorage.read(
                    k.getTenantId() == null ? 0L : k.getTenantId(), k.getFilePath());
            DocReaderClient.ParseResult parsed = docReader.read(
                    content, k.getFileName(), k.getFileType(), k.getTitle(), null);
            List<ImageRefRewriter.RefRow> rows = parsed.imageRefs() == null ? List.of()
                    : parsed.imageRefs().stream().map(r -> new ImageRefRewriter.RefRow(
                            r.originalRef(), r.filename(), r.mimeType(), r.imageData())).toList();
            markdown = extractedImageStore.storeAndRewrite(k, rows, parsed.markdown());
        } catch (RuntimeException e) {
            failStageSpan(docSpan, "DOCREADER_FAILED",
                    e.getMessage() == null ? e.toString() : e.getMessage(), e);
            throw e;
        }
        endStageSpan(docSpan, Map.of("chars", markdown.length()));
        return imageOcr.enrich(k, markdown, attempt, knowledgeId);
    }

    private SpanTracker.SpanHandle beginStageSpan(int attempt, String knowledgeId,
            String stage, Map<String, Object> input) {
        if (attempt <= 0) {
            return null;
        }
        return spanTracker.beginStage(knowledgeId, attempt, stage, input);
    }

    private void endStageSpan(SpanTracker.SpanHandle span, Map<String, Object> output) {
        if (span != null) {
            spanTracker.endSpan(span, output);
        }
    }

    private void failStageSpan(SpanTracker.SpanHandle span, String code, String message,
            Throwable error) {
        if (span != null) {
            spanTracker.failSpan(span, code, message, error);
        }
    }
}
