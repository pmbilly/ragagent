package com.ragagent.session.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * 请求段冻结：fork 共享快照、状态独立、冻结后请求段不可写。
 */
class ChatManageFreezeTest {

    private static ChatManage frozen() {
        ChatManage cm = new ChatManage();
        cm.setQuery("q");
        cm.setKnowledgeBaseIds(new java.util.ArrayList<>(java.util.List.of("kb1")));
        cm.setVectorThreshold(0.5);
        cm.setRerankThreshold(0.3);
        cm.getQueryPlan().setRewriteQuery("rewritten");
        return cm.freeze();
    }

    @Test
    void forkSharesRequestSegmentButKeepsStateIndependent() {
        ChatManage original = frozen();
        ChatManage clone = original.cloneChatManage();

        assertThat(clone.getQuery()).isEqualTo("q");
        assertThat(clone.getKnowledgeBaseIds()).containsExactly("kb1");
        assertThat(clone.getRerankThreshold()).isEqualTo(0.3);
        assertThat(clone.getQueryPlan()).isNotSameAs(original.getQueryPlan());
        // 请求段只读：summaryConfig 随快照共享，不再复制
        assertThat(clone.getSummaryConfig()).isSameAs(original.getSummaryConfig());

        clone.setRerankThreshold(0.9);
        assertThat(original.getRerankThreshold()).isEqualTo(0.3);
    }

    @Test
    void requestSegmentIsReadOnlyAfterFreeze() {
        ChatManage cm = frozen();
        assertThatThrownBy(() -> cm.setQuery("other"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cm.setChatModelId("m"))
                .isInstanceOf(IllegalStateException.class);
    }
}
