package com.ragagent.knowledge.controller;

import com.ragagent.framework.error.AppError;
import com.ragagent.framework.error.BizException;
import com.ragagent.framework.pagination.PageResult;
import com.ragagent.framework.security.LogSanitizer;
import com.ragagent.framework.tenant.TenantContext;
import com.ragagent.framework.web.ApiResponse;
import com.ragagent.framework.web.ApiResult;
import com.ragagent.framework.web.NonNullBody;
import com.ragagent.knowledge.assembler.FaqEntryAssembler;
import com.ragagent.knowledge.assembler.FaqExportAssembler;
import com.ragagent.knowledge.controller.vo.FaqEntryRespVo;
import com.ragagent.knowledge.dto.doc.UpdateLastImportDisplayStatusRequest;
import com.ragagent.knowledge.dto.faq.AddSimilarQuestionsRequest;
import com.ragagent.knowledge.dto.faq.FaqBatchUpsertPayload;
import com.ragagent.knowledge.dto.faq.FaqDeleteRequest;
import com.ragagent.knowledge.dto.faq.FaqEntryFieldsBatchUpdate;
import com.ragagent.knowledge.dto.faq.FaqEntryPayload;
import com.ragagent.knowledge.dto.faq.FaqEntryTagBatchRequest;
import com.ragagent.knowledge.dto.faq.FaqImportProgress;
import com.ragagent.knowledge.dto.faq.FaqSearchRequest;
import com.ragagent.knowledge.dto.faq.FaqTaskStartResponse;
import com.ragagent.knowledge.service.access.ChunkAccessGuard;
import com.ragagent.knowledge.service.faq.FaqEntryCommandService;
import com.ragagent.knowledge.service.faq.FaqEntryQueryService;
import com.ragagent.knowledge.service.faq.FaqImportService;
import com.ragagent.knowledge.support.KnowledgeTaskIdCodec;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FAQ 模块的 HTTP 面：绑定、访问控制与参数整形在此，业务与持久化在 service 层。
 *
 * <p>出口一律是 {@code controller.vo} 下的 VO，由 {@code assembler} 装配；领域对象不出现在
 * HTTP 契约上，因此领域模型演进不会改变响应字节。</p>
 */
@RestController
@ApiResult
@Tag(name = "Knowledge")
public class FaqController {


    private final FaqEntryQueryService faqEntryQuery;
    private final FaqEntryCommandService faqEntryCommand;
    private final FaqImportService faqImport;
    private final ChunkAccessGuard guard;

    public FaqController(FaqEntryQueryService faqEntryQuery,
                         FaqEntryCommandService faqEntryCommand,
                         FaqImportService faqImport,
                         ChunkAccessGuard guard) {
        this.faqEntryQuery = faqEntryQuery;
        this.faqEntryCommand = faqEntryCommand;
        this.faqImport = faqImport;
        this.guard = guard;
    }

    // ── 读 ──

    @GetMapping("/api/v1/knowledge-bases/{id}/faq/entries")
    public ResponseEntity<PageResult<FaqEntryRespVo>> listEntries(
            @PathVariable("id") String id,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "pageSize", required = false) String pageSize,
            @RequestParam(value = "tagIds", required = false) String tagIds,
            @RequestParam(value = "tagId", required = false) String tagId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "searchField", required = false) String searchField,
            @RequestParam(value = "sortOrder", required = false) String sortOrder,
            @RequestParam(value = "isEnabled", required = false) String isEnabled) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbRead(kbId);

        int pageValue = bindPagination(page, "page", false);
        int sizeValue = bindPagination(pageSize, "pageSize", true);
        if (pageValue < 1) {
            pageValue = 1;
        }
        if (sizeValue < 1) {
            sizeValue = 20;
        }
        if (sizeValue > 1000) {
            sizeValue = 1000;
        }

        List<String> tagUuids = parseCommaSeparatedTagIDs(tagIds);
        long legacyTagSeqId = 0;
        if (tagId != null && !tagId.isEmpty()) {
            try {
                legacyTagSeqId = Long.parseLong(tagId);
            } catch (NumberFormatException e) {
                throw new BizException(AppError.badRequest("tagId 必须是整数"));
            }
        }
        Boolean isEnabledFilter = parseOptionalFAQEnabled(isEnabled);

        return ResponseEntity.ok(FaqEntryAssembler.toVoPage(
                faqEntryQuery.listEntries(kbId, pageValue, sizeValue, tagUuids,
                        legacyTagSeqId, LogSanitizer.sanitize(keyword),
                        LogSanitizer.sanitize(searchField), LogSanitizer.sanitize(sortOrder),
                        isEnabledFilter)));
    }

    /** 导出：CSV（默认，含 BOM）或 JSON（?format=json）。 */
    @GetMapping("/api/v1/knowledge-bases/{id}/faq/entries/export")
    public ResponseEntity<byte[]> exportEntries(
            @PathVariable("id") String id,
            @RequestParam(value = "format", required = false) String format) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbRead(kbId);
        String fmt = format == null ? "" : format.toLowerCase().trim();
        if ("json".equals(fmt)) {
            return FaqExportAssembler.json(faqEntryQuery.exportJson(kbId));
        }
        return FaqExportAssembler.csv(faqEntryQuery.exportCsv(kbId));
    }

    @GetMapping("/api/v1/knowledge-bases/{id}/faq/entries/{entryId}")
    public ResponseEntity<FaqEntryRespVo> getEntry(
            @PathVariable("id") String id, @PathVariable("entryId") String entryId) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbRead(kbId);
        long entrySeqId = parseEntryId(entryId);
        return ResponseEntity.ok(FaqEntryAssembler.toVo(faqEntryQuery.getEntry(kbId, entrySeqId)));
    }

    /** 导入进度轮询面（前端 3 秒轮询依赖 404 语义与 data 内 camelCase 字段形状）。 */
    @GetMapping("/api/v1/faq/import/progress/{taskId}")
    public ResponseEntity<FaqImportProgress> getImportProgress(
            @PathVariable("taskId") String taskId) {
        String task = LogSanitizer.sanitize(taskId);
        requireTaskProgressTenant(task);
        return ResponseEntity.ok(faqImport.getImportProgress(task));
    }

    // ── 写 ──

    @PostMapping("/api/v1/knowledge-bases/{id}/faq/entries")
    public ResponseEntity<FaqTaskStartResponse> upsertEntries(
            @PathVariable("id") String id,
            @Valid @NonNullBody @RequestBody FaqBatchUpsertPayload req) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbWrite(kbId);
        String taskId = faqImport.upsertEntries(kbId, req);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new FaqTaskStartResponse(taskId));
    }

    @PostMapping("/api/v1/knowledge-bases/{id}/faq/entry")
    public ResponseEntity<FaqEntryRespVo> createEntry(
            @PathVariable("id") String id,
            @Valid @NonNullBody @RequestBody FaqEntryPayload req) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbWrite(kbId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FaqEntryAssembler.toVo(faqEntryCommand.createEntry(kbId, req)));
    }

    @PutMapping("/api/v1/knowledge-bases/{id}/faq/entries/{entryId}")
    public ResponseEntity<FaqEntryRespVo> updateEntry(
            @PathVariable("id") String id,
            @PathVariable("entryId") String entryId,
            @Valid @NonNullBody @RequestBody FaqEntryPayload req) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbWrite(kbId);
        long entrySeqId = parseEntryId(entryId);
        return ResponseEntity.ok(
                FaqEntryAssembler.toVo(faqEntryCommand.updateEntry(kbId, entrySeqId, req)));
    }

    @PostMapping("/api/v1/knowledge-bases/{id}/faq/entries/{entryId}/similar-questions")
    public ResponseEntity<FaqEntryRespVo> addSimilarQuestions(
            @PathVariable("id") String id,
            @PathVariable("entryId") String entryId,
            @Valid @NonNullBody @RequestBody AddSimilarQuestionsRequest req) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbWrite(kbId);
        long entrySeqId = parseEntryId(entryId);
        return ResponseEntity.ok(FaqEntryAssembler.toVo(
                faqEntryCommand.addSimilarQuestions(kbId, entrySeqId, req.similarQuestions())));
    }

    @PutMapping("/api/v1/knowledge-bases/{id}/faq/entries/fields")
    public ApiResponse<Void> updateEntryFieldsBatch(
            @PathVariable("id") String id,
            @Valid @NonNullBody @RequestBody FaqEntryFieldsBatchUpdate req) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbWrite(kbId);
        faqEntryCommand.updateEntryFieldsBatch(kbId, req);
        return ApiResponse.ok();   // 204 退役 ⇒ 200 + 外壳
    }

    @PutMapping("/api/v1/knowledge-bases/{id}/faq/entries/tags")
    public ApiResponse<Void> updateEntryTagBatch(
            @PathVariable("id") String id,
            @Valid @NonNullBody @RequestBody FaqEntryTagBatchRequest req) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbWrite(kbId);
        faqEntryCommand.updateEntryTagBatch(kbId, req.updates());
        return ApiResponse.ok();   // 204 退役 ⇒ 200 + 外壳
    }

    @DeleteMapping("/api/v1/knowledge-bases/{id}/faq/entries")
    public ApiResponse<Void> deleteEntries(
            @PathVariable("id") String id,
            @Valid @NonNullBody @RequestBody FaqDeleteRequest req) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbWrite(kbId);
        faqEntryCommand.deleteEntries(kbId, req.ids());
        return ApiResponse.ok();   // 204 退役 ⇒ 200 + 外壳
    }

    /** matchCount 先钳 [10,200]（service 再钳 50）。 */
    @PostMapping("/api/v1/knowledge-bases/{id}/faq/search")
    public ResponseEntity<List<FaqEntryRespVo>> searchFAQ(
            @PathVariable("id") String id,
            @Valid @NonNullBody @RequestBody FaqSearchRequest raw) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbRead(kbId);
        FaqSearchRequest req = new FaqSearchRequest(LogSanitizer.sanitize(raw.queryText()),
                raw.vectorThreshold(),
                raw.matchCount() <= 0 ? 10 : Math.min(raw.matchCount(), 200),
                raw.firstPriorityTagIds(), raw.secondPriorityTagIds(), raw.onlyRecommended());
        return ResponseEntity.ok(FaqEntryAssembler.toVoList(faqEntryQuery.searchEntries(kbId, req)));
    }

    @PutMapping("/api/v1/knowledge-bases/{id}/faq/import/last-result/display")
    public ApiResponse<Void> updateLastImportResultDisplayStatus(
            @PathVariable("id") String id,
            @Valid @NonNullBody @RequestBody UpdateLastImportDisplayStatusRequest req) {
        String kbId = LogSanitizer.sanitize(id);
        requireKbWrite(kbId);
        faqImport.updateLastImportResultDisplayStatus(kbId, req.displayStatus());
        return ApiResponse.ok();   // 204 退役 ⇒ 200 + 外壳
    }

    // ── 路由守卫 ──

    /** 读路由：解析 KB（404）→ API-Key 白名单 → 跨租户 403。 */
    private void requireKbRead(String kbId) {
        guard.requireKbAccess(kbId);
    }

    /**
     * 写路由：所有权判定先行（非创建者且非 Admin+ → 403 纯字符串），再 KB 访问层。
     * 判定顺序 契约样例依赖（faq-create-contrib / faq-list-cross），不能重排。
     */
    private void requireKbWrite(String kbId) {
        guard.requireOwnedKbInCallerSpace(kbId);
        guard.requireKbAccess(kbId);
    }

    // ── 参数解析 ──

    /** 导入进度面：任务租户必须与调用方一致，否则 404（不泄露他租户任务存在性）。 */
    private void requireTaskProgressTenant(String taskId) {
        Long taskTenantId = KnowledgeTaskIdCodec.taskTenantId(taskId);
        if (taskTenantId == null) {
            throw new BizException(AppError.badRequest("invalid task ID"));
        }
        Long callerTenantId = TenantContext.currentTenantId();
        if (callerTenantId == null || callerTenantId == 0) {
            throw new BizException(AppError.unauthorized("Unauthorized"));
        }
        if (taskTenantId.longValue() != callerTenantId.longValue()) {
            throw new BizException(AppError.notFound("task not found"));
        }
    }

    /** 逗号分隔标签 ID：滤掉空段与 __untagged__ 哨兵。 */
    private static List<String> parseCommaSeparatedTagIDs(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String p : raw.split(",", -1)) {
            String trimmed = p.trim();
            if (trimmed.isEmpty() || "__untagged__".equals(trimmed)) {
                continue;
            }
            result.add(trimmed);
        }
        return result;
    }

    /** is_enabled：缺省 null；true/false（大小写/空白宽容），其余 400。 */
    private static Boolean parseOptionalFAQEnabled(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new BizException(AppError.badRequest("is_enabled must be true or false"));
    }

    private static long parseEntryId(String entryId) {
        try {
            return Long.parseLong(entryId);
        } catch (NumberFormatException e) {
            throw new BizException(AppError.badRequest("entry_id 必须是整数"));
        }
    }

    /**
     * 分页 query 绑定：解析失败 → 400「page: 类型不正确」；&lt;1 →「必须为正整数」
     * （page=0 与缺省同义，由调用方钳 1）；page_size 上限 1000。message 统一
     * 「分页参数不合法」。
     */
    private static int bindPagination(String raw, String field, boolean withMax) {
        if (raw == null || raw.isEmpty()) {
            return 0;
        }
        final long value;
        try {
            value = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw invalidPagination(field + ": 类型不正确");
        }
        if (value == 0) {
            return 0;
        }
        if (value < 1) {
            throw invalidPagination(field + ": 必须为正整数");
        }
        if (withMax && value > 1000) {
            throw invalidPagination(field + ": 必须不大于 1000");
        }
        return (int) value;
    }

    private static BizException invalidPagination(String errText) {
        return new BizException(AppError.badRequest("分页参数不合法").withDetails(errText));
    }

}
