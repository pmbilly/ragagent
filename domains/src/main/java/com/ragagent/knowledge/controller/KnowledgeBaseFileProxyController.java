package com.ragagent.knowledge.controller;

import java.io.IOException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import com.ragagent.common.knowledge.domain.KnowledgeBase;
import com.ragagent.knowledge.domain.access.ChunkAccessGuard;
import com.ragagent.common.storage.fileserve.FileProxyPort;
import com.ragagent.common.storage.fileserve.FileServeHttp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.ragagent.platform.web.ApiResult;

/**
 * newKBScopedFileServeHandlerWithResources + access.ResolveKBFile）。
 * <p>渲染知识库内容（chunk / wiki 页）内嵌图片用。与租户级 /files 不同，本路由
 * 消费 RequireKBAccess 的<b>精确 KB grant</b>：文件必须属于 KB 属主租户、在
 * {@code exports/} 命名空间下、且有该 KB 内存活文档的显式绑定
 * （IsReferencedByKnowledgeBase——文本提到 handle 不算所有权证据）。</p>
 * → AllowFileServeAPIKey → g.Viewer()（RbacInterceptor 规则）→ KBAccessRead
 * → ResolveKBFile → serveAuthorizedFile。</p>
 */
@RestController
@ApiResult
public class KnowledgeBaseFileProxyController {

    private final ChunkAccessGuard kbGuard;
    private final FileProxyPort fileProxy;

    public KnowledgeBaseFileProxyController(ChunkAccessGuard kbGuard, FileProxyPort fileProxy) {
        this.kbGuard = kbGuard;
        this.fileProxy = fileProxy;
    }

    @RequestMapping(value = "/api/v1/knowledge-bases/{id}/files", method = RequestMethod.HEAD)
    public void filesHead(@PathVariable("id") String id, HttpServletResponse response)
            throws IOException {
        FileServeHttp.writeGinNoRoute(response);
    }

    @GetMapping("/api/v1/knowledge-bases/{id}/files")
    public void files(@PathVariable("id") String id, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        // AllowFileServeAPIKey 由 AllowFileServeAPIKeyInterceptor 承担（WebConfig 接线，
        // found" / 403 "Permission denied..." 信封（ChunkAccessGuard 内逐字实现）
        // 闸门异常**不**在此吞掉：它走全局信封（黄金契约 w5c-kbfiles-missing-kb.json 锁定形态）
        KnowledgeBase kb = kbGuard.requireKbAccess(id);
        fileProxy.serveKbFile(kb == null ? null : kb.getTenantId(), id, request, response);
    }
}
