-- V6：knowledge_bases 两个 jsonb 列的**默认值**键名统一到 Java 字段名（camelCase）（B93c）。
--
-- 背景：V2 已在**数据层**把 KB 配置 jsonb 的键改名（45 条映射，含 chunk_size→chunkSize、
-- model_id→modelId 等），代码侧（读/写）也已全部 camel；**残留的只有 DDL 默认值** ——
-- 它仍是 Go 时代的 snake 形态，导致「未显式给配置」插入的行会带回旧键（与代码读侧不一致，
-- 属静默失配面）。
--
-- 范围（实测）：全库 jsonb 列扫描后，仅 knowledge_bases.chunking_config 与
-- knowledge_bases.image_processing_config 两列的 DEFAULT 命中；其余列（含 V2/V3 已迁移的
-- KB/agent 配置）数据与代码两侧均已是 camel。
--
-- 幂等：ALTER COLUMN ... SET DEFAULT 本身幂等，可重复执行。
--
-- 不做的两件事（有意）：
--   ① 不 UPDATE 存量行 —— V2 已把带旧键的行重写为 camel（其映射覆盖这四个/两个键）；
--   ② 不改列名与 SQL —— 表列名/查询参数按 SQL 惯例保留 snake（§2.4 的适用面是 JSON 键）。

ALTER TABLE knowledge_bases
    ALTER COLUMN chunking_config SET DEFAULT
    '{"chunkSize": 512, "chunkOverlap": 50, "splitMarkers": ["\n\n", "\n", "。"], "keepSeparator": true}'::jsonb;

ALTER TABLE knowledge_bases
    ALTER COLUMN image_processing_config SET DEFAULT
    '{"modelId": "", "enableMultimodal": false}'::jsonb;
