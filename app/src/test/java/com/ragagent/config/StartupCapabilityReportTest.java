package com.ragagent.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupCapabilityReportTest {

    @TempDir
    Path tempDir;

    @Test
    void writableDirectoryPasses() {
        StartupCapabilityReport.verifyStorageWritable(tempDir.toString());
    }

    @Test
    void fileAsBaseDirectoryFails() throws IOException {
        Path file = tempDir.resolve("not-a-directory");
        Files.writeString(file, "x");

        assertThatThrownBy(() -> StartupCapabilityReport.verifyStorageWritable(file.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("不可写");
    }
}
