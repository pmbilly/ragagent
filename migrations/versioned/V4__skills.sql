-- V4：平台级技能目录（指令型技能入库）
--
-- 背景：技能 = 指令型 SKILL.md（提示词注入，模型凭指令用现有工具执行；无脚本执行面）。
-- B57 起技能从「宿主目录文件」改为**数据库存储**——硬需求是多实例部署下的一致性
-- （各实例本地目录不同步，文件方案会出现“同一技能在不同实例可见性不同”）。
--
-- 范围：新增 skills 单表。平台级（不带 tenant_id；将来要空间级时加 scope/tenant_id 列即可，
-- 接口与前端不用改）。content 存组装好的 SKILL.md 原文（frontmatter + 正文），
-- 服务端复用 Skill.parseSkillFile 做解析与校验（失败即拒绝写入）。
-- 管理面：/api/v1/skills/catalog（SystemAdmin）；选择器 GET /api/v1/skills（Viewer）读同表。
--
-- 幂等：CREATE TABLE/INDEX IF NOT EXISTS；重复执行为空操作。
-- 回滚：无自动 down 迁移（Flyway 社区版不支持）；如需回滚手工 DROP TABLE skills。

CREATE TABLE IF NOT EXISTS skills (
    id          varchar(64) PRIMARY KEY,
    slug        varchar(64)  NOT NULL,
    name        varchar(128) NOT NULL,
    description text         NOT NULL DEFAULT '',
    content     text         NOT NULL,
    version     int          NOT NULL DEFAULT 1,
    created_by  varchar(64)  NOT NULL DEFAULT '',
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted_at  timestamptz
);

-- 软删后同名 slug 可重建：唯一性只约束未删行。
CREATE UNIQUE INDEX IF NOT EXISTS uq_skills_slug_active ON skills (slug) WHERE deleted_at IS NULL;
