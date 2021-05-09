package com.secKillingProject.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.secKillingProject.serializer.JodaDateTimeJsonDeserializer;
import com.secKillingProject.serializer.JodaDateTimeJsonSerializer;
import org.joda.time.DateTime;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.stereotype.Component;

/**
 * @author fucker
 * 使用redis做操作了
 * maxInactiveIntervalInSeconds的默认参数是1800，代表默认存30分钟
 * 改成3600，即是默认存60分钟
 */
@Component
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600)
public class RedisConfig {
    /**自定义redisTemplate，自定义的改掉默认的序列化方式。
     * 就可以解决序列化后可读性差的问题了*/
    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory){
        RedisTemplate redisTemplate = new RedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        //首先解决K的序列化,定义的序列化方式为String
        StringRedisSerializer stringRedisSerializer =new StringRedisSerializer();
        //使用模板中提供的修改序列化的方式，将stringRedisSerializer放进去，它就会使用String方式
        redisTemplate.setKeySerializer(stringRedisSerializer);

        //解决value的序列化方式，value的序列化一般使用JSON。默认构造函数选Object.class
        Jackson2JsonRedisSerializer jackson2JsonRedisSerializer =new Jackson2JsonRedisSerializer(Object.class);

        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        simpleModule.addSerializer(DateTime.class,new JodaDateTimeJsonSerializer());
        simpleModule.addDeserializer(DateTime.class,new JodaDateTimeJsonDeserializer());

        //对Redis的value，JSON序列化时加上类信息。
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);

        //注册simpleModule
        objectMapper.registerModule(simpleModule);
        //完成绑定，将设置后的JackSon放进Redis的设置中。但是已经序列化redis中的value，就难直接反序列化加类信息了
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
        redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);

        return redisTemplate;
    }
}
