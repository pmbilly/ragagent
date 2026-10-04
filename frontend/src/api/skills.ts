import { del, get, post } from '@/utils/request'

/**
 * 指令型技能（平台级技能库）。
 *
 * 技能 = 一段 SKILL.md 指令（frontmatter + 正文），运行期注入智能体提示词，
 * 模型凭指令用现有工具执行——无脚本执行面、无安装。B57 起技能**入库**
 * （skills 表，多实例共享），宿主目录扫描已退役。
 *
 * 选择器（智能体编辑器）读 `GET /api/v1/skills`（Viewer）；
 * 管理面为 `GET/POST/DELETE /api/v1/skills/catalog**`（SystemAdmin）。
 */
export interface InstructionalSkillInfo {
  name: string
  description: string
}

/** 引用该技能的智能体（删除保护的提示来源）。 */
export interface SkillReference {
  agentId: string
  agentName: string
  /** true = 该智能体是「全部技能」模式（隐式引用）。 */
  allMode: boolean
}

export interface SkillCatalogItem {
  id: string
  slug: string
  name: string
  description: string
  version: number
  createdBy: string
  createdAt: string
  updatedAt: string
  referencedBy: SkillReference[]
}

export interface SkillFileEntry {
  name: string
  path: string
}

export interface CreateSkillPayload {
  slug: string
  name: string
  description: string
  /** 指令正文（不含 frontmatter；frontmatter 由服务端组装）。 */
  content: string
}

/** 选择器数据源：智能体编辑器 → 技能区（Viewer）。 */
export function listSkills() {
  return get<{ skills: InstructionalSkillInfo[]; skillsAvailable: boolean }>('/api/v1/skills')
}

/** 技能库列表（SystemAdmin）。 */
export function listSkillCatalog() {
  return get<{ skills: SkillCatalogItem[] }>('/api/v1/skills/catalog')
}

/** 新建技能（SystemAdmin）；frontmatter 由服务端组装并自校验。 */
export function createSkill(data: CreateSkillPayload) {
  return post<SkillCatalogItem>('/api/v1/skills/catalog', data)
}

/**
 * 删除技能（软删，SystemAdmin）。被智能体引用时后端返回 409 + 引用清单；
 * `force=true` 才强删（调用方必须先向用户展示影响面）。
 */
export function deleteSkill(id: string, force = false) {
  return del(`/api/v1/skills/catalog/${id}${force ? '?force=true' : ''}`)
}

/** 技能文件清单（入库技能只有 SKILL.md，接口形状沿用裁剪前）。 */
export function listSkillFiles(id: string) {
  return get<{ files: SkillFileEntry[] }>(`/api/v1/skills/catalog/${id}/files`)
}

/** 技能文件内容（完整 SKILL.md 原文，含 frontmatter）。 */
export function getSkillFileContent(id: string, path = 'SKILL.md') {
  return get<{ name: string; path: string; content: string }>(
    `/api/v1/skills/catalog/${id}/files/content`,
    { params: { path } },
  )
}
