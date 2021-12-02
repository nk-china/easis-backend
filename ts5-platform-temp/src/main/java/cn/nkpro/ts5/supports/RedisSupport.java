package cn.nkpro.ts5.supports;

import cn.nkpro.ts5.exception.TfmsException;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Collection;
import java.util.Map;

/**
 * Created by bean on 2020/7/24.
 */
public interface RedisSupport<T> {

    @Scheduled(cron = "0 * * * * ?")
    void heartbeat();

    T getIfAbsent(String hash,String hashKey,Function<T> mapper) throws TfmsException;
    T getIfAbsent(String hash,String hashKey,boolean cacheNullValue,Function<T> mapper) throws TfmsException;
    Map<String, T> getHash(String hash, Collection<String> keys);
    void putHash(String hash, String key, T value);

    T getIfAbsent(String key, Function<T> mapper) throws TfmsException;
    T getIfAbsent(String key,boolean cacheNullValue,Function<T> mapper) throws TfmsException;
    void set(String key, T value);

    void delete(String key);
    void delete(String hash, Object... hashKey);
    void deletes(String keysLike);

    Long increment(String key, long l);

    T get(String key);

    /**
     *
     * @param timeout 单位 秒
     */
    void expire(String key, long timeout);

    void clear();

    Boolean hasKey(String key);

    interface Function<T>{
        T apply() throws TfmsException;
    }
}
