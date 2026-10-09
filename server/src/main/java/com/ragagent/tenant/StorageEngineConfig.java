package com.ragagent.tenant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 存储引擎配置段（8 个 provider 子结构）。
 *
 * <p>defaultProvider 恒输出（零值 ""）；8 个 provider 子结构
 * 为 null 时省略键。provider 子结构内部字段**全部恒输出**——
 * 对象一旦存在，所有键恒输出（含 "" 与 false），golden ct-kv-storage-* 钉住。</p>
 */

public class StorageEngineConfig {

    private String defaultProvider = "";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalEngineConfig local;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private MinioEngineConfig minio;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CosEngineConfig cos;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private TosEngineConfig tos;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private S3EngineConfig s3;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private OssEngineConfig oss;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Ks3EngineConfig ks3;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private ObsEngineConfig obs;

    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String v) { defaultProvider = v == null ? "" : v; }
    public LocalEngineConfig getLocal() { return local; }
    public void setLocal(LocalEngineConfig v) { local = v; }
    public MinioEngineConfig getMinio() { return minio; }
    public void setMinio(MinioEngineConfig v) { minio = v; }
    public CosEngineConfig getCos() { return cos; }
    public void setCos(CosEngineConfig v) { cos = v; }
    public TosEngineConfig getTos() { return tos; }
    public void setTos(TosEngineConfig v) { tos = v; }
    public S3EngineConfig getS3() { return s3; }
    public void setS3(S3EngineConfig v) { s3 = v; }
    public OssEngineConfig getOss() { return oss; }
    public void setOss(OssEngineConfig v) { oss = v; }
    public Ks3EngineConfig getKs3() { return ks3; }
    public void setKs3(Ks3EngineConfig v) { ks3 = v; }
    public ObsEngineConfig getObs() { return obs; }
    public void setObs(ObsEngineConfig v) { obs = v; }

    public static class LocalEngineConfig {
        private String pathPrefix = "";

        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String v) { pathPrefix = v == null ? "" : v; }
    }

    public static class MinioEngineConfig {
        private String mode = "";
        private String endpoint = "";
        private String accessKeyId = "";
        private String secretAccessKey = "";
        private String bucketName = "";
        private boolean useSsl;
        private String pathPrefix = "";

        public String getMode() { return mode; }
        public void setMode(String v) { mode = v == null ? "" : v; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { endpoint = v == null ? "" : v; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String v) { accessKeyId = v == null ? "" : v; }
        public String getSecretAccessKey() { return secretAccessKey; }
        public void setSecretAccessKey(String v) { secretAccessKey = v == null ? "" : v; }
        public String getBucketName() { return bucketName; }
        public void setBucketName(String v) { bucketName = v == null ? "" : v; }
        public boolean isUseSsl() { return useSsl; }
        public void setUseSsl(boolean v) { useSsl = v; }
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String v) { pathPrefix = v == null ? "" : v; }
    }

    public static class CosEngineConfig {
        private String secretId = "";
        private String secretKey = "";
        private String region = "";
        private String bucketName = "";
        private String appId = "";
        private String pathPrefix = "";
        private String tempBucketName = "";
        private String tempRegion = "";

        public String getSecretId() { return secretId; }
        public void setSecretId(String v) { secretId = v == null ? "" : v; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String v) { secretKey = v == null ? "" : v; }
        public String getRegion() { return region; }
        public void setRegion(String v) { region = v == null ? "" : v; }
        public String getBucketName() { return bucketName; }
        public void setBucketName(String v) { bucketName = v == null ? "" : v; }
        public String getAppId() { return appId; }
        public void setAppId(String v) { appId = v == null ? "" : v; }
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String v) { pathPrefix = v == null ? "" : v; }
        public String getTempBucketName() { return tempBucketName; }
        public void setTempBucketName(String v) { tempBucketName = v == null ? "" : v; }
        public String getTempRegion() { return tempRegion; }
        public void setTempRegion(String v) { tempRegion = v == null ? "" : v; }
    }

    public static class TosEngineConfig {
        private String endpoint = "";
        private String region = "";
        private String accessKey = "";
        private String secretKey = "";
        private String bucketName = "";
        private String pathPrefix = "";
        private String tempBucketName = "";
        private String tempRegion = "";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { endpoint = v == null ? "" : v; }
        public String getRegion() { return region; }
        public void setRegion(String v) { region = v == null ? "" : v; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String v) { accessKey = v == null ? "" : v; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String v) { secretKey = v == null ? "" : v; }
        public String getBucketName() { return bucketName; }
        public void setBucketName(String v) { bucketName = v == null ? "" : v; }
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String v) { pathPrefix = v == null ? "" : v; }
        public String getTempBucketName() { return tempBucketName; }
        public void setTempBucketName(String v) { tempBucketName = v == null ? "" : v; }
        public String getTempRegion() { return tempRegion; }
        public void setTempRegion(String v) { tempRegion = v == null ? "" : v; }
    }

    public static class S3EngineConfig {
        private String endpoint = "";
        private String region = "";
        private String accessKey = "";
        private String secretKey = "";
        private String bucketName = "";
        private String pathPrefix = "";
        private boolean useSsl;
        private boolean forcePathStyle;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { endpoint = v == null ? "" : v; }
        public String getRegion() { return region; }
        public void setRegion(String v) { region = v == null ? "" : v; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String v) { accessKey = v == null ? "" : v; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String v) { secretKey = v == null ? "" : v; }
        public String getBucketName() { return bucketName; }
        public void setBucketName(String v) { bucketName = v == null ? "" : v; }
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String v) { pathPrefix = v == null ? "" : v; }
        public boolean isUseSsl() { return useSsl; }
        public void setUseSsl(boolean v) { useSsl = v; }
        public boolean isForcePathStyle() { return forcePathStyle; }
        public void setForcePathStyle(boolean v) { forcePathStyle = v; }
    }

    public static class OssEngineConfig {
        private String endpoint = "";
        private String region = "";
        private String accessKey = "";
        private String secretKey = "";
        private String bucketName = "";
        private String pathPrefix = "";
        private boolean useTempBucket;
        private String tempBucketName = "";
        private String tempRegion = "";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { endpoint = v == null ? "" : v; }
        public String getRegion() { return region; }
        public void setRegion(String v) { region = v == null ? "" : v; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String v) { accessKey = v == null ? "" : v; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String v) { secretKey = v == null ? "" : v; }
        public String getBucketName() { return bucketName; }
        public void setBucketName(String v) { bucketName = v == null ? "" : v; }
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String v) { pathPrefix = v == null ? "" : v; }
        public boolean isUseTempBucket() { return useTempBucket; }
        public void setUseTempBucket(boolean v) { useTempBucket = v; }
        public String getTempBucketName() { return tempBucketName; }
        public void setTempBucketName(String v) { tempBucketName = v == null ? "" : v; }
        public String getTempRegion() { return tempRegion; }
        public void setTempRegion(String v) { tempRegion = v == null ? "" : v; }
    }

    public static class Ks3EngineConfig {
        private String endpoint = "";
        private String region = "";
        private String accessKey = "";
        private String secretKey = "";
        private String bucketName = "";
        private String pathPrefix = "";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { endpoint = v == null ? "" : v; }
        public String getRegion() { return region; }
        public void setRegion(String v) { region = v == null ? "" : v; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String v) { accessKey = v == null ? "" : v; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String v) { secretKey = v == null ? "" : v; }
        public String getBucketName() { return bucketName; }
        public void setBucketName(String v) { bucketName = v == null ? "" : v; }
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String v) { pathPrefix = v == null ? "" : v; }
    }

    public static class ObsEngineConfig {
        private String endpoint = "";
        private String region = "";
        private String accessKey = "";
        private String secretKey = "";
        private String bucketName = "";
        private String pathPrefix = "";
        private boolean useSsl;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String v) { endpoint = v == null ? "" : v; }
        public String getRegion() { return region; }
        public void setRegion(String v) { region = v == null ? "" : v; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String v) { accessKey = v == null ? "" : v; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String v) { secretKey = v == null ? "" : v; }
        public String getBucketName() { return bucketName; }
        public void setBucketName(String v) { bucketName = v == null ? "" : v; }
        public String getPathPrefix() { return pathPrefix; }
        public void setPathPrefix(String v) { pathPrefix = v == null ? "" : v; }
        public boolean isUseSsl() { return useSsl; }
        public void setUseSsl(boolean v) { useSsl = v; }
    }
}
