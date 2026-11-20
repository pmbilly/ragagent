package com.ragagent.config;

import com.ragagent.llm.ollama.OllamaService;
import com.ragagent.platform.prompt.ExtractPrompts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型运行时的两个进程级 bean（组合根收拢）。
 *
 * <p>原先这两个 {@code @Bean} 挂在业务域（{@code initialization.service.InitializationWiring}）
 * ——那是"跨模块组装权落在业务域"的典型；按计划"跨模块协作经 contract、由 boot 注入"归组合根。</p>
 *
 * <p>{@link OllamaService} 的 {@code isAvailable} 是**跨请求共享状态**——"已可用则跳过 StartService"
 * 的分支依赖 bean 级单例，不能每请求新建。</p>
 */
@Configuration
public class ModelRuntimeBeansWiring {

    @Bean
    public OllamaService ollamaService() {
        // 基址取 OLLAMA_BASE_URL（缺省 localhost:11434），
        // OLLAMA_OPTIONAL=true 时可用性失败不报错。单例语义由 bean 层承担。
        return OllamaService.getOllamaService();
    }

    @Bean
    public ExtractPrompts extractPrompts() {
        return new ExtractPrompts();
    }
}
