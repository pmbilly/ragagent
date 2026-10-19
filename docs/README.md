# 文档索引

文档分两轨：**新人向**（仓库根 `README.md` + 本目录）与**维护者向**（`internal/`，迁移/整改的过程记录）。新人不要从 `internal/` 开始读。

## 新人路径（按顺序）

| 顺序 | 文档 | 内容 |
|---|---|---|
| 1 | [`../README.md`](../README.md) | 项目是什么、四条命令跑起来 |
| 2 | [`getting-started.md`](getting-started.md) | 30 / 60 / 120 分钟上手与常见失败对照 |
| 3 | [`architecture/overview.md`](architecture/overview.md) | 7 个模块、依赖矩阵、代码在哪 |
| 4 | [`architecture/request-path.md`](architecture/request-path.md) | 一次问答经过哪些模块与类、日志关键字 |
| 5 | [`architecture/rag-pipeline.md`](architecture/rag-pipeline.md) | 14 个管线阶段与插件装配表 |
| 6 | [`architecture/ingest-flow.md`](architecture/ingest-flow.md) | 一份文档从入口到入库、富化与收口的一页纸 |
| 7 | [`architecture/mechanisms.md`](architecture/mechanisms.md) | 队列、事务、embedding、定时任务等反直觉机制 |
| 8 | [`glossary.md`](glossary.md) | 业务术语与历史代号（K4 / A18 / R3d / B2xx） |

查表：[`architecture/packages.md`](architecture/packages.md) —— 337 份 `package-info` 的包索引（由脚本生成，勿手改）。

先看模块：[`architecture/module-package-map.md`](architecture/module-package-map.md) —— 7 个模块的包根、22 个业务域的包/控制器数量（由脚本生成，勿手改）。

先看业务分类：[`architecture/domain-index.md`](architecture/domain-index.md) —— 22 个域按 6 类能力组织，含入口、契约面与前端页面（由脚本生成，勿手改）。

查权限：[`architecture/route-policy-map.md`](architecture/route-policy-map.md) —— 386 条路由 → RBAC 角色下限/公开自服务登记（由脚本生成，勿手改）。

> 代码注释里的「HANDOFF §x」指 [`internal/HANDOFF.md`](internal/HANDOFF.md)。

## 维护者路径

- [`internal/HANDOFF.md`](internal/HANDOFF.md) —— 当前批次、判定标准、闸门与回填
- [`internal/plans/`](internal/plans/) —— 整改方案（v2 与历史版本）
- [`internal/handoff/`](internal/handoff/) —— 批次记录、专项计划与归档

## 约定

- 新人向文档只写**当前事实**；过程、批次号、临时读数一律进 `internal/`。
- `internal/` 里的路径与数字会过期；动手前以代码、守卫和 `git log` 为准。
