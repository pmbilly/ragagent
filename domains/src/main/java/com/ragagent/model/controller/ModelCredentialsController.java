package com.ragagent.model.controller;

import com.ragagent.contract.error.AppError;
import com.ragagent.contract.error.BizException;
import com.ragagent.model.dto.ModelCredentialsPutRequest;
import com.ragagent.model.dto.ModelCredentialsResponse;
import com.ragagent.model.service.ModelService;
import com.ragagent.model.service.ModelService.ModelNotFoundException;
import com.ragagent.platform.access.TenantContext;
import com.ragagent.platform.web.ApiResponse;
import com.ragagent.platform.web.ApiResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型凭证子资源端点：秘密字段永不经主资源 PUT 正文，只能经本资源按字段写入/清除；
 * 双字段均缺省时 PUT 退化为"已配置状态查询"。
 *
 * <p>{@code {field}} 取值域为 {@code api_key} / {@code app_secret}（字段标识符，
 * 与响应里的 fields 键一致）。</p>
 */
@RestController
@ApiResult
@RequestMapping("/api/v1/models")
@Tag(name = "Model")
public class ModelCredentialsController {


    private final ModelService modelService;

    public ModelCredentialsController(ModelService modelService) {
        this.modelService = modelService;
    }

    /** 写入/清除凭据（Admin+）；两字段均缺省时返回已配置状态。 */
    @PutMapping("/{id}/credentials")
    public ResponseEntity<ModelCredentialsResponse> put(@PathVariable("id") String id,
            @RequestBody(required = false) ModelCredentialsPutRequest req) {
        Long tenantId = TenantContext.currentTenantId();
        if (tenantId == null || tenantId == 0) {
            throw new BizException(AppError.badRequest("Workspace ID cannot be empty"));
        }
        if (req == null) {
            req = new ModelCredentialsPutRequest(null, null);
        }
        if (req.apiKey() == null && req.appSecret() == null) {
            try {
                return ResponseEntity.ok(modelService.credentialsResponse(id));
            } catch (ModelNotFoundException e) {
                throw new BizException(AppError.notFound("Model not found"));
            }
        }
        try {
            return ResponseEntity.ok(modelService.updateCredentialsResponse(
                    id, req.apiKey(), req.appSecret()));
        } catch (ModelNotFoundException e) {
            throw new BizException(AppError.notFound("Model not found"));
        }
    }

    /** 清除单个凭据字段（Admin+）→ 204。 */
    @DeleteMapping("/{id}/credentials/{field}")
    public ApiResponse<Void> deleteField(@PathVariable("id") String id,
                                            @PathVariable("field") String field) {
        Long tenantId = TenantContext.currentTenantId();
        if (tenantId == null || tenantId == 0) {
            throw new BizException(AppError.badRequest("Workspace ID cannot be empty"));
        }
        if (!"apiKey".equals(field) && !"appSecret".equals(field)) {
            throw new BizException(AppError.badRequest("unknown credential field: " + field));
        }
        try {
            modelService.clearModelCredential(id, field);
        } catch (ModelNotFoundException e) {
            throw new BizException(AppError.notFound("Model not found"));
        }
        return ApiResponse.ok();   // 204 退役（空体与「外壳恒存在」冲突）
    }
}
