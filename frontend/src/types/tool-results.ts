/**
 * Tool Results Type Definitions
 * TypeScript interfaces for all tool result types
 */

// Relevance levels — values match the backend API response.
// Display labels are resolved via i18n in SearchResults.vue and GraphQueryResults.vue.
export type RelevanceLevel = 'High Relevance' | 'Medium Relevance' | 'Low Relevance' | 'Weak Relevance';

// Display types
export type DisplayType =
    | 'search_results'
    | 'chunk_detail'
    | 'related_chunks'
    | 'knowledge_base_list'
    | 'document_info'
    | 'graph_query_results'
    | 'thinking'
    | 'plan'
    | 'database_query'
    | 'web_search_results'
    | 'web_fetch_results'
    | 'grep_results'
    | 'knowledge_chunks_list'
    | 'wiki_write_page'
    | 'wiki_replace_text'
    | 'wiki_rename_page'
    | 'wiki_delete_page'
    | 'mcp_discovery'
    | 'mcp_call';

// Search result item
export interface SearchResultItem {
    resultIndex: number;
    chunkId: string;
    content: string;
    score: number;
    relevanceLevel: RelevanceLevel;
    knowledgeId: string;
    knowledgeBaseId?: string;
    knowledgeTitle: string;
    matchType: string;
    knowledgeBaseType?: string;
    // FAQ entries share the owning document's title; the standard question
    // gives each entry a distinct, human-readable label.
    faqStandardQuestion?: string;
    faqSimilarQuestions?: string[];
    faqAnswers?: string[];
}

// Chunk item
export interface ChunkItem {
    index: number;
    chunkId: string;
    chunkIndex: number;
    content: string;
    knowledgeId: string;
}

// Knowledge base item
export interface KnowledgeBaseItem {
    index: number;
    id: string;
    name: string;
    description: string;
}

// Graph config
export interface GraphConfig {
    nodes: string[];
    relations: string[];
}

// Search results data
export interface SearchResultsData {
    displayType: 'search_results';
    results?: SearchResultItem[];
    count?: number;
    kbCounts?: Record<string, number>;
    query?: string;
    knowledgeBaseId?: string;
}

// Chunk detail data
export interface ChunkDetailData {
    displayType: 'chunk_detail';
    chunkId: string;
    content: string;
    chunkIndex: number;
    knowledgeId: string;
    contentLength?: number;
}

// Related chunks data
export interface RelatedChunksData {
    displayType: 'related_chunks';
    chunkId: string;
    count: number;
    chunks: ChunkItem[];
}

// Knowledge base list data
export interface KnowledgeBaseListData {
    displayType: 'knowledge_base_list';
    knowledge_bases: KnowledgeBaseItem[];
    count: number;
}

// Document info data
export interface DocumentInfoDocument {
    knowledgeId?: string;
    faqId?: string;
    chunkId?: string;
    title: string;
    faqQuestion?: string;
    faqAnswers?: string[];
    faqSimilarQuestions?: string[];
    isFaq?: boolean;
    description?: string;
    type?: string;
    source?: string;
    channel?: string;
    fileName?: string;
    fileType?: string;
    fileSize?: number;
    parseStatus?: string;
    chunkCount?: number;
    metadata?: Record<string, any>;
}

export interface DocumentInfoData {
    displayType: 'document_info';
    documents?: DocumentInfoDocument[];
    totalDocs: number;
    requested: number;
    errors?: string[];
    title?: string;
}

// Graph query results data
export interface GraphQueryResultsData {
    displayType: 'graph_query_results';
    results: SearchResultItem[];
    count: number;
    graphConfig: GraphConfig;
}

// Thinking data
export interface ThinkingData {
    displayType: 'thinking';
    thought: string;
}

// Plan step
export interface PlanStep {
    id: string;
    description: string;
    status: 'pending' | 'in_progress' | 'completed' | 'skipped';
}

// Plan data
export interface PlanData {
    displayType: 'plan';
    task: string;
    steps: PlanStep[];
    totalSteps: number;
}

// Database query data
export interface DatabaseQueryData {
    displayType: 'database_query';
    columns: string[];
    rows: Array<Record<string, any>>;
    rowCount: number;
}

// Web search result item
export interface WebSearchResultItem {
    resultIndex: number;
    title: string;
    url: string;
    snippet?: string;
    content?: string;
    source?: string;
    publishedAt?: string;
    age?: string;
    pageStatus?: 'success' | 'failed';
    pageVerified?: boolean;
    pageContent?: string;
    pageError?: string;
    pageTruncated?: boolean;
    fullOutputPath?: string;
    storageError?: string;
}

// Web search results data
export interface WebSearchResultsData {
    displayType: 'web_search_results';
    query: string;
    results: WebSearchResultItem[];
    count: number;
}

// Web fetch result item
export interface WebFetchResultItem {
    url: string;
    status?: 'success' | 'failed' | 'skipped';
    retryable?: boolean;
    errorCode?: string;
    error_message?: string;
    summary?: string;
    summary_status?: string;
    summary_error_code?: string;
    summary_error_message?: string;
    fullOutputPath?: string;
    storageError?: string;
    rawContent?: string;
    contentLength?: number;
    offset?: number;
    returnedChars?: number;
    truncated?: boolean;
    nextOffset?: number;
    method?: string;
    /** @deprecated use error_message */
    error?: string;
}

// Web fetch results data
export interface WebFetchResultsData {
    displayType: 'web_fetch_results';
    results: WebFetchResultItem[];
    count?: number;
    successfulCount?: number;
    failedCount?: number;
    skippedCount?: number;
    allFailed?: boolean;
}

// Grep knowledge aggregation item (legacy, grouped by knowledgeId)
export interface GrepKnowledgeResult {
    knowledgeId: string;
    knowledgeBaseId: string;
    knowledgeTitle: string;
    faqQuestion?: string;
    titleMatch?: boolean;
    chunkHitCount: number;
    matchSnippet?: string;
    patternCounts: Record<string, number>;
    totalPatternHits: number;
    distinctPatterns: number;
}

// Per-chunk grep hit (preferred for UI — one row per FAQ entry or chunk)
export interface GrepChunkResult {
    chunkId: string;
    faqId?: string;
    knowledgeId: string;
    knowledgeBaseId: string;
    knowledgeTitle: string;
    chunkType?: string;
    index?: number;
    chunkIndex?: number;
    faqQuestion?: string;
    titleMatch?: boolean;
    matchSnippet?: string;
    score?: number;
}

// Grep results data
export interface GrepResultsData {
    displayType: 'grep_results';
    query?: string;
    patterns: string[];
    chunkResults?: GrepChunkResult[];
    knowledgeResults: GrepKnowledgeResult[];
    resultCount: number;
    documentCount?: number;
    totalMatches: number;
    knowledgeBaseIds?: string[];
    limit?: number;
    maxResults: number;
}

// Knowledge chunks list data (list_knowledge_chunks tool)
export interface KnowledgeChunksListData {
    displayType: 'knowledge_chunks_list';
    knowledgeId?: string;
    knowledgeTitle?: string;
    totalChunks?: number;
    fetchedChunks?: number;
    page?: number;
    pageSize?: number;
    faqQuestion?: string;
    faqId?: string;
    singleChunk?: boolean;
}

// Wiki write page data
export interface WikiWritePageData {
    displayType: 'wiki_write_page';
    action: 'created' | 'updated';
    slug: string;
    title: string;
    pageType: string;
    summary: string;
}

// Wiki replace text data
export interface WikiReplaceTextData {
    displayType: 'wiki_replace_text';
    slug: string;
    title: string;
    oldText: string;
    newText: string;
}

// Wiki rename page data
export interface WikiRenamePageData {
    displayType: 'wiki_rename_page';
    oldSlug: string;
    newSlug: string;
    title: string;
    updatedCount: number;
    affectedPages?: string[];
}

// Wiki delete page data
export interface WikiDeletePageData {
    displayType: 'wiki_delete_page';
    slug: string;
    title: string;
    updatedCount: number;
    affectedPages?: string[];
}

// Union type for all wiki edit data
export type WikiEditData = WikiWritePageData | WikiReplaceTextData | WikiRenamePageData | WikiDeletePageData;

// Union type for all tool result data
export type ToolResultData =
    | SearchResultsData
    | ChunkDetailData
    | RelatedChunksData
    | KnowledgeBaseListData
    | DocumentInfoData
    | GraphQueryResultsData
    | ThinkingData
    | PlanData
    | DatabaseQueryData
    | WebSearchResultsData
    | WebFetchResultsData
    | GrepResultsData
    | KnowledgeChunksListData
    | WikiWritePageData
    | WikiReplaceTextData
    | WikiRenamePageData
    | WikiDeletePageData;

// Action data (from index.vue)
export interface ActionData {
    description: string;
    success: boolean;
    tool_name?: string;
    arguments?: any;
    output?: string;
    error?: string;
    details?: boolean;
    displayType?: DisplayType;
    tool_data?: Record<string, any>;
}
