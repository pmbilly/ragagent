package com.ragagent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.ragagent.session.service.rag.EventManager;
import com.ragagent.session.service.rag.PipelineEventType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 管线完整性：每个 PipelineEventType 都必须至少有一个插件处理。
 *
 * <p>新增阶段只加枚举、忘了在 QaWiring 注册时，这里会红——比线上"阶段静默跳过"早。</p>
 */
@SpringBootTest
class PipelineRegistryCompletenessTest {

    @Autowired
    EventManager eventManager;

    @Test
    void everyStageHasAtLeastOnePlugin() {
        assertThat(eventManager.registeredStages())
                .containsExactlyInAnyOrder(PipelineEventType.values());
    }
}
