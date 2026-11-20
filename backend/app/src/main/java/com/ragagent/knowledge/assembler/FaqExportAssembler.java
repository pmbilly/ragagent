package com.ragagent.knowledge.assembler;

import org.springframework.http.ResponseEntity;

/** FAQ 导出响应：CSV 带 UTF-8 BOM，JSON 不带；两者都以下载头返回。 */
public final class FaqExportAssembler {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private FaqExportAssembler() {
    }

    /** CSV：前置 BOM，供表格软件按 UTF-8 识别。 */
    public static ResponseEntity<byte[]> csv(byte[] csvData) {
        byte[] body = new byte[UTF8_BOM.length + csvData.length];
        System.arraycopy(UTF8_BOM, 0, body, 0, UTF8_BOM.length);
        System.arraycopy(csvData, 0, body, UTF8_BOM.length, csvData.length);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=faq_export.csv")
                .body(body);
    }

    /** JSON：原样返回，不加 BOM。 */
    public static ResponseEntity<byte[]> json(byte[] jsonData) {
        return ResponseEntity.ok()
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=faq_export.json")
                .body(jsonData);
    }
}
