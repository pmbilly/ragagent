/**
 * FAQ 面（从 {@code service} 整簇切出的第一个子面）。
 *
 * <p>成员：导入编排（{@code FaqImportService} + 其批次/校验协作者 {@code FaqBatchOps}/{@code FaqValidateOps}）、
 * 条目读写（{@code FaqEntryCommandService}/{@code FaqEntryQueryService}）、索引与形态（{@code FaqIndexWriter}/
 * {@code FaqIndexRows}/{@code FaqChunkCodec}）。</p>
 *
 * <p><b>为什么能整簇切出</b>：先按图算过闭包——本集**对外零依赖**（{@code FAQ → 外部} = 0），
 * 外部对它的**代码边**也为零（唯一命中是 {@code service/package-info} 的 javadoc）⇒ 切出后
 * {@code service} 与 {@code service.faq} 之间不产生任何包间边，SCC 不扩大。</p>
 */
package com.ragagent.knowledge.service.faq;
