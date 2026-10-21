package com.ragagent.model.controller;

import com.ragagent.model.service.OllamaManageService;
import com.ragagent.platform.web.ApiResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ollama 管理端点（模型域）：状态 / 本地模型 / 检查 / 下载与任务进度。
 *
 * <p>URL 前缀 {@code /api/v1/models/ollama/**} 与所属域一致；初始化向导只保留
 * 未保存表单的连通性预检（见 {@code initialization}）。</p>
 */
@RestController
@ApiResult
@RequestMapping("/api/v1/models/ollama")
@Tag(name = "Model")
public class ModelOllamaController {

    private final OllamaManageService ollamaManage;

    public ModelOllamaController(OllamaManageService ollamaManage) {
        this.ollamaManage = ollamaManage;
    }

    @GetMapping("/status")
    public ResponseEntity<Object> ollamaStatus() {
        return ResponseEntity.ok(ollamaManage.ollamaStatus());
    }

    @GetMapping("/models")
    public ResponseEntity<Object> ollamaModels() {
        return ResponseEntity.ok(ollamaManage.ollamaModels());
    }

    @PostMapping("/models/check")
    public ResponseEntity<Object> ollamaModelsCheck(@RequestBody(required = false) String rawBody) {
        return ResponseEntity.ok(ollamaManage.ollamaModelsCheck(rawBody));
    }

    @PostMapping("/models/download")
    public ResponseEntity<Object> ollamaModelDownload(@RequestBody(required = false) String rawBody) {
        return ResponseEntity.ok(ollamaManage.ollamaModelDownload(rawBody));
    }

    @GetMapping("/download/progress/{taskId}")
    public ResponseEntity<Object> downloadProgress(@PathVariable("taskId") String taskId) {
        return ResponseEntity.ok(ollamaManage.downloadProgress(taskId));
    }

    @GetMapping("/download/tasks")
    public ResponseEntity<Object> downloadTasksList() {
        return ResponseEntity.ok(ollamaManage.downloadTasksList());
    }
}
