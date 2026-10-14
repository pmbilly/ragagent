/**
 * 消息实体面（由 {@code session.domain} 切出，对齐方案 §8.8.1）：{@code Message} 与其
 * 附件/图片/工件/搜索/建议集（{@code MessageArtifact} / {@code MessageSuggestionSet} …）+
 * artifact 版本化（{@code ArtifactVersions}）。
 *
 * <p><b>为什么 {@code ArtifactVersions} 在这里</b>：它原留 {@code session.domain}，但它引用
 * {@code MessageArtifact}，而 message 簇大量引用留存类 ⇒ 照搬会造 {@code domain ⇄ domain.message}
 * **双环**；实测其外部引用者仅 {@code session/service/MessageService} ⇒ 随 message 族走，
 * 使簇间边成为 {@code message → domain} 单向。</p>
 */
package com.ragagent.session.domain.message;
