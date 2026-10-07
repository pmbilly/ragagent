package com.ragagent.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 字段校验文案与 PascalCase → camelCase 转写的钉子。 */
class RequestFieldsTest {

    @Test
    void messageUsesCamelCaseFieldAndPlainPhrases() {
        assertThat(RequestFields.message("Name", "required")).isEqualTo("field 'name' is required");
        assertThat(RequestFields.message("Email", "email"))
                .isEqualTo("field 'email' is not a valid email address");
        assertThat(RequestFields.message("Password", "min")).isEqualTo("field 'password' is below the minimum");
        assertThat(RequestFields.message("Username", "max"))
                .isEqualTo("field 'username' is above the maximum");
        assertThat(RequestFields.message("Query", "oneof")).isEqualTo("field 'query' failed validation: oneof");
        assertThat(RequestFields.wrongType("name", "string", "number"))
                .isEqualTo("field 'name' must be a string, got number");
        assertThat(RequestFields.wrongType("TenantID", "integer", "string"))
                .isEqualTo("field 'tenantId' must be an integer, got string");
    }

    @Test
    void fieldNameConvertsAcronyms() {
        assertThat(RequestFields.fieldName("TenantID")).isEqualTo("tenantId");
        assertThat(RequestFields.fieldName("LLMModelID")).isEqualTo("llmModelId");
        assertThat(RequestFields.fieldName("ChunkSize")).isEqualTo("chunkSize");
        assertThat(RequestFields.fieldName("DocumentSplitting")).isEqualTo("documentSplitting");
        assertThat(RequestFields.fieldName("tenantId")).isEqualTo("tenantId");
        assertThat(RequestFields.fieldName("")).isEmpty();
    }
}
