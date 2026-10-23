/**
 * Embed i18n entry point.
 *
 * - Visitor UI strings: `frontend/src/i18n/locales/embed/<lang>.ts`（一个语言一个文件）
 * - Admin 语言包: `frontend/src/i18n/locales/<lang>.ts`（管理后台，与访客侧互不依赖）
 * - `frontend/src/i18n/embed.ts` 只放 i18n 实例与语言协商（不存文案）
 *
 * Re-export locale helpers so feature code can import from one path.
 */
export {
  applyEmbedLocale,
  EMBED_LOCALE_STORAGE_KEY,
  EMBED_MESSAGES,
  normalizeEmbedLocale,
  readEmbedLocaleFromUrl,
  SUPPORTED_LOCALES,
  syncEmbedLocaleFromUrl,
  type EmbedLocale,
} from '@/i18n/embed'
