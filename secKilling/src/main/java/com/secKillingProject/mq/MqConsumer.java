package com.secKillingProject.mq;

//import com.sun.org.apache.xpath.internal.operations.String;

import com.alibaba.fastjson.JSON;
import com.secKillingProject.dao.ItemStockDOMapper;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * 消费者拿到消息体（BodyMap），然后根据定义的规则去消费消息
 *即通过BodyMap中的itemId和amount，调用StockDOMapper去减少库存
 * 所谓的 消费消息 就是根据定义的消费(使用数据的)方式去使用消息中的数据
 *  异步减库存这一操作交给了MQ的消费者，
 *  定义好消费数据的方式，消费者就会根据消费方式和数据去减库存
 *
 *  MQ这里实际上就像是一个异步的定时任务/自动化脚本，
 *  任务/脚本都是自定义的，触发条件也是自定义的。
 *  MQ根据触发条件去执行异步任务
 *  */

@Component
public class MqConsumer {
    /**定义consumer 同样类似autowired注解下的注入*/
    private DefaultMQPushConsumer consumer;

    /**
     * @Value() 注入application properties中的变量
     * 这里就是通过注入 mq.nameserver.addr
     * 拿到对应的值 47.97.20.97:9876
     * 即nameAddr 的值为 47.97.20.97:9876
     * */
    @Value("${mq.nameserver.addr}")
    private String nameAddr;

    /**这里同样是通过@value 拿到mq.topicname中的值
     * 即 topicName的值为 stock
     * */
    @Value("${mq.topicname}")
    private String topicName;

    /**注入itemStockDOMapper,通过StockDO操作DB*/
    @Autowired
    private ItemStockDOMapper itemStockDOMapper;

    @PostConstruct
    public void init() throws MQClientException {
        /**通过new拿到consumer对象
         *consumer group name为 "stock_consumer_group"
         * */
        consumer = new DefaultMQPushConsumer("stock_consumer_group");

        consumer = new DefaultMQPushConsumer("stock_consumer_group");
        consumer.setNamesrvAddr(nameAddr);
        consumer.subscribe(topicName,"*");



        /**拿到nameServer具体的地址，相当于绑定地址*/
        consumer.setNamesrvAddr(nameAddr);



        /**指定consumer监听哪一个topic的消息
         * 即监听刚刚从配置文件中读到的topic
         * *表示监听该topic的所有消息
         * */
        consumer.subscribe(topicName,"*");


        /***
         * 确定消息推送过来之后如何处理？
         *
         * 完成了MQ的接入
         */
        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                /**consumer端从Brock中拿到消息
                 * 传入消息的序号，0即是第一条消息
                 * 拿到第一条消息，然后通过getBody的方式拿到消息体，
                 * 即bodyMap
                 * 然后MQ根据定义的消费数据的方式
                 * --通过StockDOMapper的方式去更新数据*/
                Message msg = msgs.get(0);
                String jsonString = new String(msg.getBody());
//                JSON String转Map
                Map<String,Object> map = JSON.parseObject(jsonString,Map.class);
                //拿到Map中的itemId,amount
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");

                /**核心操作根据从Redis中获取的Id和amount
                 * 【调用StockDOMapper的更新库存的方式去减库存】
                 * 以Redis的数据为准，传amount进SQL，
                 * DB执行SQL达成数据同步
                 * 这便是更新数据/减库存。
                 * 根据某个值为准，将这个值赋给表中的记录 --这便是更新
                 * * */
                itemStockDOMapper.decreaseStock(itemId,amount);

                //返回CONSUME_SUCCESS，代表这个消息已经被consumer消费，下次不会再投放了
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

    }
}
