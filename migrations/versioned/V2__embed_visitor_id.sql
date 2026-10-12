-- embed 访客历史会话：会话行记录创建它的访客 id（浏览器本地随机串，非登录身份）。
-- 仅 embed 会话有值；Web/API/IM 会话为 NULL。
ALTER TABLE public.sessions ADD COLUMN embed_visitor_id character varying(128);


-- 历史会话列表查询（tenant + 访客 + 最后活动倒序）：只索引有值的行。
CREATE INDEX idx_sessions_tenant_embed_visitor ON public.sessions USING btree (tenant_id, embed_visitor_id, updated_at DESC) WHERE ((embed_visitor_id IS NOT NULL) AND (deleted_at IS NULL));
