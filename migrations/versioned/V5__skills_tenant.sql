-- 技能从「平台级」调整为「租户级」（B60）。
--
-- 语义：
--   tenant_id 非空 = 该空间的技能（本空间可见、本空间管理员可读写）；
--   tenant_id IS NULL = **平台内置层**（官方预置，全员可见但只读，
--   运行时对所有空间可见、对所有租户管理员只读）。
--
-- 唯一性：同一「命名空间」内 slug 唯一（未删行）。平台层用 COALESCE(tenant_id, 0)
-- 折叠成一个伪空间 0（真实租户 id ≥ 1），因此平台内置与各租户可使用同名 slug；
-- name（运行期身份，agent 的 selectedSkills 存的是它）的唯一性由服务层校验。
--
-- 幂等：ADD COLUMN / CREATE INDEX IF NOT EXISTS；DROP INDEX IF EXISTS。
-- 存量数据：本批上线前库里只有演示数据，由发布流程一次性归属到目标空间
-- （见 docs/handoff 记录），迁移本身不改数据。

ALTER TABLE skills ADD COLUMN IF NOT EXISTS tenant_id INTEGER;

COMMENT ON COLUMN skills.tenant_id IS '所属空间 id；NULL = 平台内置（全员可见、只读）';

-- 旧的全局 slug 唯一索引退场：改为 (命名空间, slug)
DROP INDEX IF EXISTS uq_skills_slug_active;

CREATE UNIQUE INDEX IF NOT EXISTS uq_skills_scope_slug_active
    ON skills (COALESCE(tenant_id, 0), slug) WHERE deleted_at IS NULL;

-- 选择器/目录按「平台层 + 当前空间」查询
CREATE INDEX IF NOT EXISTS ix_skills_tenant_active ON skills (tenant_id) WHERE deleted_at IS NULL;
