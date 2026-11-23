package com.ragagent.framework.concurrent;

import java.time.Duration;
import java.util.Optional;

/**
 * 分布式锁与去重标记的统一端口。
 *
 * <p>两种语义分开：<b>互斥锁</b>（tryLock → 执行 → close 释放，释放时校验持有者
 * 标识防误删他锁）与<b>去重标记</b>（markOnce，SET NX EX 不删、靠 TTL 过期）。
 * 互斥锁的释放是「Lua 比对 token 后才 DEL」——TTL 到期后锁被别的持有者重建时，
 * 原持有者的 close 不会删掉新持有者的锁。</p>
 *
 * <p>实现：{@code RedisDistributedLock}（StringRedisTemplate + Lua）。</p>
 */
public interface DistributedLock {

    /**
     * 互斥锁：SET NX EX 取锁，拿到返回 {@link LockHandle}，拿不到返回 {@code Optional.empty()}。
     * 调用方在 try-with-resources 里使用（close 自动释放）。
     */
    Optional<LockHandle> tryLock(String key, Duration ttl);

    /** 去重标记：SET NX EX 写入 "1"（不删，靠 TTL 过期）。返回是否首次设置成功。 */
    default boolean markOnce(String key, Duration ttl) {
        return markOnce(key, "1", ttl);
    }

    /** 去重/选主标记：SET NX EX 写入指定 value（不删，靠 TTL 过期）。返回是否首次设置成功。 */
    boolean markOnce(String key, String value, Duration ttl);

    /** 互斥锁的持有句柄。 */
    interface LockHandle extends AutoCloseable {

        /** 续期：EXPIRE key 回满 TTL（长任务防锁过期）。 */
        void renew();

        /** 释放：Lua 校验「值仍是自己的 token」后才 DEL（防误删他锁）。 */
        @Override
        void close();
    }
}
