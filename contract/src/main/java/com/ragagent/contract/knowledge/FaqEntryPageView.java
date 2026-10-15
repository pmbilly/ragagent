package com.ragagent.contract.knowledge;

import java.util.List;

/** FAQ 条目分页视图（形状与域内 `FaqEntryPage` 同形：items/page/pageSize/total）。 */
public record FaqEntryPageView(List<FaqEntryView> items, int page, int pageSize, long total) {
}
