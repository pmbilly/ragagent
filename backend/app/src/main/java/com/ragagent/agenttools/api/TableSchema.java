package com.ragagent.agenttools.api;

import java.util.List;

/** 表 schema：表名 + 列 + 行数。 */
public record TableSchema(String tableName, List<ColumnInfo> columns, long rowCount) {
}
