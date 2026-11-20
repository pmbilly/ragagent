package com.ragagent.knowledge.service.management;

import java.util.Locale;

/**
 * URL 导入的 fileType 决议：显式值 > 响应 Content-Type > 文件名扩展名 > html。
 *
 * <p>网页 URL 常以无扩展名路径结尾（如 {@code /aion-v/2024}），fileType 必须由
 * Content-Type 判定且不留空——docreader 对空类型直接报 {@code Unsupported file type}。</p>
 */
final class UrlFileTypes {

    private UrlFileTypes() {
    }

    static String resolve(String requested, String fileName, String contentType) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim().toLowerCase(Locale.ROOT);
        }
        String mapped = fromContentType(contentType);
        if (!mapped.isEmpty()) {
            return mapped;
        }
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        return "html";
    }

    /** Content-Type → docreader 认的扩展名；未知返回空串。 */
    static String fromContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        String ct = contentType.toLowerCase(Locale.ROOT).split(";")[0].trim();
        return switch (ct) {
            case "text/html", "application/xhtml+xml" -> "html";
            case "text/plain" -> "txt";
            case "text/markdown" -> "md";
            case "text/csv" -> "csv";
            case "application/json" -> "json";
            case "application/pdf" -> "pdf";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.ms-powerpoint" -> "ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx";
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "";
        };
    }
}
