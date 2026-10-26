-- 知识解析代次：每次重解析 +1；状态 CAS 与失败清场按代次隔离新旧尝试。
-- 背景：重解析会把文档重置为 pending 并再入队，若没有代次，旧 attempt 仍能
-- 把新 attempt 的行改成 completed/failed，或按知识粒度删掉新 attempt 的向量。
-- 幂等：存量环境可能已手工补列，避免迁移因「列已存在」失败。
ALTER TABLE public.knowledges ADD COLUMN IF NOT EXISTS parse_attempt bigint NOT NULL DEFAULT 0;
