package com.ragagent.framework.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * {@code conversation.*} 配置绑定的形状钉：前缀、键名与字段一一对应。
 *
 * <p>线上意义：该类经 app 的 {@code @ConfigurationPropertiesScan} 注册——若前缀/字段名漂移，
 * 绑定会静默落空（属性全 null/默认值、无编译错无启动错）。本测试用 Binder 直连，
 * 钉住「yml 键 → 字段」这条路；注册在名单里这一步由 app 侧全上下文测试兜底。</p>
 */
class ConversationPropertiesBindTest {

    @Test
    void bindsConversationPrefixToFields() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "conversation.max-rounds", "9",
                "conversation.keyword-threshold", "0.55",
                "conversation.embedding-top-k", "42"));
        ConversationProperties props = new Binder(source)
                .bind("conversation", Bindable.of(ConversationProperties.class))
                .get();
        assertThat(props.getMaxRounds()).isEqualTo(9);
        assertThat(props.getKeywordThreshold()).isEqualTo(0.55);
        assertThat(props.getEmbeddingTopK()).isEqualTo(42);
    }

    @Test
    void defaultsSurviveUnboundKeys() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of());
        ConversationProperties props = new Binder(source)
                .bind("conversation", Bindable.of(ConversationProperties.class))
                .orElse(new ConversationProperties());
        assertThat(props.getMaxRounds()).isEqualTo(5);
        assertThat(props.getKeywordThreshold()).isEqualTo(0.3);
    }
}
