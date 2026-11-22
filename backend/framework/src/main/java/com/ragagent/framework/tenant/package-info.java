/**
 * 多租户与访问控制（基础设施）：请求级租户上下文（TenantContext 及其跨线程作用域）、租户角色、租户配置读取面、API Key 作用域。原 access 包（请求上下文）已并入本包——"当前租户"的读取、角色与作用域只此一处可寻。
 *
 * <p>这些形状被 framework 的 Web / 持久化 / 安全适配层与各业务域共同引用，
 * 属基础设施能力，不是业务词汇。</p>
 */
package com.ragagent.framework.tenant;
