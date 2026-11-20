/**
 * 系统设置与管理员面：系统设置读写（{@code /api/v1/system}）与管理员操作
 * （{@code /api/v1/system/admin}）。设置项键名是前后端共享契约，改动需同批前端。
 *
 * <p><b>种类</b>：控制面<br>
 * <b>入口</b>：{@code SystemController}、{@code SystemAdminController}<br>
 * <b>对外契约面</b>：{@code SystemSettingGateway}<br>
 * <b>依赖</b>：{@code contract}、{@code platform}、同模块 {@code auth}。</p>
 */
package com.ragagent.system;
