package com.ragagent.contract.knowledge.service;

import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.error.ErrorCode;
import com.ragagent.contract.knowledge.KnowledgeRowView;

/**
 * 上传内容与库内已有文档重复（409）。
 *
 * <p>继承 {@link BizException}：错误体由全局异常处理器统一产出
 * （{@code {error:{code,message,details}, success:false}}），已存在文档的 ID 放
 * {@code details.existingKnowledgeId} 供前端跳转——取代历史上的特殊信封
 * （{@code {code,data,message,success}}）。</p>
 *
 * <p><b>为什么在 {@code :contract}</b>（§16.2 二轮裁定）：它被 {@code datasource} /
 * {@code im} / {@code chatpipeline} / {@code knowledge} **多个域**抛出或捕获，且依赖面
 * **全部是契约件**（{@code BizException} / {@code AppError} / {@code KnowledgeRowView} /
 * {@code DuplicateKnowledgeDetails}）⇒ 符合契约既有的"跨域异常"先例
 * （{@code WikiException} / {@code ResourceModeException} / {@code ToolApprovalException} 等）；
 * 留在 L1 会让契约异常面与 L1 异常面分裂。</p>
 */
public class DuplicateKnowledgeException extends BizException {
    private final KnowledgeRowView existing;

    public DuplicateKnowledgeException(KnowledgeRowView existing, ErrorCode code, String message) {
        super(new AppError(code.value(), message, null, 409)
                .withDetails(existing == null ? null
                        : new DuplicateKnowledgeDetails(existing.getId())));
        this.existing = existing;
    }

    public KnowledgeRowView existing() { return existing; }
}
