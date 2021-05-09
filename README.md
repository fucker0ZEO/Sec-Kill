# Sec-Kill
秒杀项目
部署于4台阿里云2核8G的ESC上，2台Java应用服务器，1台数据库服务器，1台Nginx
后端技术栈：Redis+RocketMQ+SpringBoot+Nginx Lua+Guava Cache+Guava RateLimiter
Redis实现单点登录
Nginx实现负载均衡
Redis+Guava Cache+Nginx Lua实现对商品详情页的多级缓存
Redis实现秒杀库存缓存
MQ实现Redis和MySQL的数据同步，事务性消息保证最终一致性
活动令牌实现幂等
令牌闸门+线程池本地队列实现流量削峰（后续可以使用MQ替代）
Guava RateLimiter 实现令牌桶限流（后续可以使用sentinel替代）
验证码实现流量错峰


