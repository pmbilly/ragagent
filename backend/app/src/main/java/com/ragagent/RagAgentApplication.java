package com.ragagent;



import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan("com.ragagent.**.mapper")
// 配置属性类分布在两处：组合根 config/ 与跨域共享的 platform 配置包（TenantProperties 在此）。
// 新增共享配置类时把包加进这个列表——别写 com.ragagent 根包扫描，
// 那会把 session 等处"未注册"的配置类一并绑定，属行为变化。
@ConfigurationPropertiesScan({
        "com.ragagent.config",
        "com.ragagent.storage.config",
        "com.ragagent.framework.config", "com.ragagent.knowledge.config",
        "com.ragagent.framework.security", "com.ragagent.framework.storage.config", "com.ragagent.auth.config", "com.ragagent.retrieval.graph",
        "com.ragagent.modelruntime.stream", "com.ragagent.framework.tracing.langfuse"})
public class RagAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagAgentApplication.class, args);
    }
}
