package com.ragagent.im.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import jakarta.annotation.PreDestroy;

/**
 * 生命周期接线契约：IM 运行时的两个触发点**必须挂在门面**（{@link ImService}，它是 Spring bean）上，
 * 且**只挂在那里**。
 *
 * <p>判据来自一次真实事故：B127 把「渠道运行时」整块从门面外提到 {@link ImChannelRuntimeOps}
 * 时，`@EventListener` 与 `@PreDestroy` **跟着方法一起搬走了** —— 那个类不是 bean ⇒ 两个钩子
 * 静默失效（渠道不再随应用启动、停机不再清理），而编译与 4,700+ 全量测试**全部照绿**。
 * ArchUnit A13 能兜住"注解挂在非 bean 上"，但兜不住"注解被整个删掉"⇒ 这里把契约钉死。</p>
 */
class ImLifecycleWiringTest {

    @Test
    @DisplayName("门面的 startChannelsOnReady 带 @EventListener(ApplicationReadyEvent)")
    void startHookOnFacade() throws Exception {
        Method m = ImService.class.getMethod("startChannelsOnReady");
        assertThat(m.getAnnotation(EventListener.class))
                .as("渠道随应用启动的唯一入口（B128 事故点）")
                .isNotNull();
        assertThat(m.getAnnotation(EventListener.class).value())
                .as("必须监听 ApplicationReadyEvent")
                .containsExactly(ApplicationReadyEvent.class);
    }

    @Test
    @DisplayName("门面的 stop 带 @PreDestroy")
    void stopHookOnFacade() throws Exception {
        Method m = ImService.class.getMethod("stop");
        assertThat(m.getAnnotation(PreDestroy.class)).as("停机清理的唯一入口（B128 事故点）").isNotNull();
    }

    @Test
    @DisplayName("运行时协作者类不得自行声明生命周期钩子（唯一入口原则）")
    void noHooksOnNonBeanCollaborators() {
        for (Method m : ImChannelRuntimeOps.class.getDeclaredMethods()) {
            assertThat(m.getAnnotation(EventListener.class)).as("ImChannelRuntimeOps#%s 不是 bean，"
                    + "钩子挂这里会静默失效", m.getName()).isNull();
            assertThat(m.getAnnotation(PreDestroy.class)).as("ImChannelRuntimeOps#%s 同上", m.getName()).isNull();
        }
    }
}
