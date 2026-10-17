package com.ragagent.knowledge.api;

import com.ragagent.contract.doc.DocReaderParsePort;
import com.ragagent.knowledge.client.DocReaderClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * {@link DocReaderParsePort} 的 knowledge 侧实现：把解析请求转发给
 * {@link DocReaderClient}，并把域内解析结果映射成契约视图。
 *
 * <p>映射口径与迁移前调用方直接消费 {@code ParseResult} 一致：{@code markdown}/{@code imageCount}
 * 原样透传；{@code imageRefs} 为 null 时给空列表（调用方对两者同判）。</p>
 */
@Component
public class DocReaderParseAdapter implements DocReaderParsePort {

    private final DocReaderClient client;

    public DocReaderParseAdapter(DocReaderClient client) {
        this.client = client;
    }

    @Override
    public ParsedDocumentView parse(byte[] fileContent, String fileName, String fileType,
                                    String title, String parserEngine) throws Exception {
        DocReaderClient.ParseResult result =
                client.read(fileContent, fileName, fileType, title, parserEngine);
        if (result == null) {
            return null;
        }
        List<ImageRefView> refs = new ArrayList<>();
        if (result.imageRefs() != null) {
            for (DocReaderClient.ImageRef ref : result.imageRefs()) {
                refs.add(new ImageRefView(ref.filename(), ref.originalRef(),
                        ref.mimeType(), ref.imageData()));
            }
        }
        return new ParsedDocumentView(result.markdown(), result.imageCount(), refs);
    }
}
