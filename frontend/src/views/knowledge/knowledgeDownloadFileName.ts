export interface KnowledgeDownloadItem {
  id: string;
  originalFileName?: string;
  fileName?: string;
  title?: string;
  type?: string;
}

export function resolveKnowledgeDownloadFileName(item: KnowledgeDownloadItem): string {
  const baseName = item.originalFileName || item.fileName || item.title || item.id;
  if (item.type === 'manual' && !baseName.toLowerCase().endsWith('.md')) {
    return `${baseName}.md`;
  }
  return baseName;
}
