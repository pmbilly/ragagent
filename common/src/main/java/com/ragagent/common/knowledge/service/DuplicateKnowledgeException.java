package com.ragagent.common.knowledge.service;

import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.contract.error.ErrorCode;
import com.ragagent.common.knowledge.KnowledgeRowView;
import com.ragagent.contract.knowledge.service.DuplicateKnowledgeDetails;

/**
 * 重复知识冲突（由 {@code KnowledgeService} 的**嵌套类**提为**顶层类型**）。
 *
 * <p>它被 {@code datasource}/{@code im}/{@code chatpipeline} 等多个域以
 * {@code DuplicateKnowledgeException} 形式引用——嵌套在门面里会让这些域
 * 反向依赖 799 行的枢纽类；提为 {@code domain/} 顶层后按普通域异常引用。</p>
 */
/**
 * 上传内容与库内已有文档重复（409）。
 *
 * <p>继承 {@link BizException}：错误体由全局异常处理器统一产出
 * （{@code {error:{code,message,details}, success:false}}），已存在文档的 ID 放
 * {@code details.existingKnowledgeId} 供前端跳转——取代历史上的特殊信封
 * （{@code {code,data,message,success}}）。</p>
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
