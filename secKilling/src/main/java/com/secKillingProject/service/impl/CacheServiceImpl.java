package com.secKillingProject.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.secKillingProject.service.CacheService;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Service
public class CacheServiceImpl implements CacheService {

    /**引入Guava中的cache*/
    private Cache<String,Object> commonCache =null;

    /** @PostConstruct 注解，
     * 这样Spirng在加载时就会优先去执行这个init方法
     * new出commonCache对象*/
    @PostConstruct
    public void init(){
        commonCache = CacheBuilder.newBuilder()
                //这个参数是设置缓存容器的初始容量为10
                .initialCapacity(10)
                //缓存容量的大小最多为100个key，超过100个后会按照LRU的策略淘汰缓存
                .maximumSize(100)
                //设置过期时间,这个间是写入的时间，不是被访问的时间
                .expireAfterWrite(60, TimeUnit.SECONDS).build();


    }

    /**存入本地缓存,使用put方法*/
    @Override
    public void setCommonCache(String key, Object value) {
        commonCache.put(key,value);

    }

    /**从本地缓存中取出,使用get方法*/
    @Override
    public Object getFromCommonCache(String key) {
        return commonCache.getIfPresent(key);
    }
}
