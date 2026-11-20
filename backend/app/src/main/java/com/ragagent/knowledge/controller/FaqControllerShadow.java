package com.ragagent.knowledge.controller;

import com.ragagent.knowledge.service.access.ChunkAccessGuard;
import com.ragagent.knowledge.service.faq.FaqEntryCommandService;
import com.ragagent.knowledge.service.faq.FaqEntryQueryService;
import com.ragagent.knowledge.service.faq.FaqImportService;
import com.ragagent.platform.web.ApiResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAQ HTTP 面的替换实现：与 {@link FaqController} 同路由，只在 {@code faq-new} profile 下激活。
 *
 * <p>两个实现互斥：默认 profile 用 {@code FaqController}，{@code faq-new} profile 用本类。
 * 同一批 {@code faq-*} 金片分别跑在两者上，两侧响应体必须逐字节一致，这是该 API 面的等效性判据。</p>
 *
 * <p>本类直接继承 {@code FaqController}，因此只承载"路由可替换"这一件事；接口形状与响应外壳同父类。
 * {@code @ApiResult} 必须一并声明：统一响应外壳由该注解驱动，缺了它两侧线格式会不同。</p>
 */
@RestController
@ApiResult
@Tag(name = "Knowledge")
@Profile("faq-new")
public class FaqControllerShadow extends FaqController {

    public FaqControllerShadow(FaqEntryQueryService faqEntryQuery,
                               FaqEntryCommandService faqEntryCommand,
                               FaqImportService faqImport,
                               ChunkAccessGuard guard) {
        super(faqEntryQuery, faqEntryCommand, faqImport, guard);
    }
}
