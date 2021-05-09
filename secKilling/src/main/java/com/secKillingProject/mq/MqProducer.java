package com.secKillingProject.mq;

import com.alibaba.fastjson.JSON;
import com.secKillingProject.dao.StockLogDOMapper;
import com.secKillingProject.dataObject.StockLogDO;
import com.secKillingProject.error.BusinessException;
import com.secKillingProject.service.OrderService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**@author fucker
 * @Component 使用这个标签声明它是Spring的Bean
 * @PostConstruct bean的初始化后完成被调用，
 * 一般调用自定义的是自定义的初始化方法。
 * 即bean初始化完就调用自己的定义的MQ初始化方法
 * 类似的是容器的生命周期，好好理解生命周期这个概念
 * 生命周期指必须经过的几个流程，每一个流程中又有着固定的事件要完成
 * 例如初始化，最开始必须要做什么事。就像是人出生时会哭。
 * 人的具体的行为活动从第一声啼哭开始，
 * 我们可以看出人的初始化阶段做了啼哭这个事件
 *
 * 类似的consumer也是这样声明*/
@Component
public class MqProducer {
/**    定义默认的producer，同样类似autowired注解下的注入*/
    private DefaultMQProducer producer;

    /**使用事务型消息*/
    private TransactionMQProducer transactionMQProducer;
   /**引入订单service，后面在本地事务中创建订单*/
    @Autowired
    private OrderService orderService;
    /**注入StockLogDOMapper*/
    @Autowired
    private StockLogDOMapper stockLogDOMapper;

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



    @PostConstruct
    public void init() throws MQClientException {
        //做MQ producer的初始化,并指明producer的group name
        // 为"producer_group"
        //对于rocketMQ来说，producer的groupName无意义，只是一个标识性的存在
        //consumer的groupName倒是有意义

       producer = new DefaultMQProducer("producer_group");

       //初始化地址。拿到前面从配置文件中注入的值]i
          producer.setNamesrvAddr(nameAddr);
        //调用start方法，中间件就会去连接对应的操作.
        producer.start();

        transactionMQProducer =new TransactionMQProducer("transaction_producer_group");
        //初始化地址。拿到前面从配置文件中注入的值]i
        transactionMQProducer.setNamesrvAddr(nameAddr);
        //调用start方法，中间件就会去连接对应的操作.
        transactionMQProducer.start();
        //新建一个TransactionListener
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                //真正要做的事，生产者发送事务型消息后会执行本地事务
                //拿到argsMap传给本地事务的4个参数
                Integer itemId= (Integer) ((Map)arg).get("itemId");
                Integer promoId= (Integer) ((Map)arg).get("promoId");
                Integer userId= (Integer) ((Map)arg).get("userId");
                Integer amount= (Integer) ((Map)arg).get("amount");
                String stockLogId = (String) ((Map)arg).get("stockLogId");

                //本地事务中创建订单
                /*看上去只是一个创建订单，实际上这个方法内是减库存+创建订单+增加销量 */
                try {
                    orderService.createOrder(userId,itemId,promoId,amount,stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    //失败则将流水状态设置为3
                    stockLogDO.setStatus(3);
                    //整行记录写回DB。看似需要会写DB，占用大量资源，
                    // 实际上这个是针对交易订单的，和库存无关，
                    // 库存耗费资源的核心原因是库存的锁并发竞争问题。
                    // 订单则是每一笔订单都是独立的，没这个问题，性能会好很多。
                    // 锁并发竞争下IO才是真的慢，竞争就要等，IO中也要等
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    //创建订单有异常则回滚事务(本地的MySQL自动回滚了)，并返回RollBACK给MQ server
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                //事务型消息执行完后，会返回commit,rollback,UN KNOW.
                //这里是成功，就返回commit
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            /**如果是un know状态则执行checkLocalTransaction
             * 根据库存是否扣减成功来判断COMMit还是RollBack
             * 还是继续UN KNOW*/
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                String jsonString = new String(msg.getBody());
//                JSON String转Map
                Map<String,Object> map = JSON.parseObject(jsonString,Map.class);
                //拿到Map中的itemId,amount
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");
                String stockLogId = (String) map.get("stockLogId");


                //现在只有amount和itemId，没法判断具体是否成功，需要拿到订单流水！
                //根据库存流水号，拿到具体的库存流水记录
                StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);

                //只要从DB、其他端 取值都要做判空处理
                if (stockLogDO == null){
                    //待会再来问,UN KNOW
                    return LocalTransactionState.UNKNOW;
                }
                //根据库存流水记录拿到库存流水号，最后根据库存流水号判断是否成功
                Integer status = stockLogDO.getStatus();
                if (status == 2){
                    //成成功即可扣减库存
                    return LocalTransactionState.COMMIT_MESSAGE;
                }else if(status == 1){
                    return LocalTransactionState.UNKNOW;

                }else {
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
            }



        });

    }


    /**初始化之后就做具体的事--同步库存扣减消息
     * SendResult 代表了消息发送的状态
     * 拿到发过来的itemId和amount
     *
     * 定义要传过去的数据”打包“放在一起 bodyMap
     * 打包的数据bodyMap放进消息对象message中
     * 消息对象message通过producer.send方法投放出去
     * */
    public boolean asyncReduceStock(Integer itemId,Integer amount)  {
        /*投放消息--定义用来投放消息的map
        body代表消息体
        * 消息体有两个部分itemId和amount，itemId和amount放进消息体中*/
        HashMap<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId",itemId);
        bodyMap.put("amount",amount);

        //创建一条消息,前后2个massage都可能有坑，IDEA自动导包，导的是SUN里的Message。而非NQ里的Message
         Message message = new org.apache.rocketmq.common.message.Message(topicName,"increase",
                 //将消息map转成JSON的string放进message中
                 JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        try {
            //将message通过生产者的send方法投放过去（投放给broker）
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    //事务型库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId,Integer itemId,Integer promoId,Integer amount,String stockLogId){
           /*投放消息--定义用来投放消息的map
        body代表消息体
        * 消息体有两个部分itemId和amount，itemId和amount放进消息体中*/
        HashMap<String, Object> bodyMap = new HashMap<>();
        bodyMap.put("itemId",itemId);
        bodyMap.put("amount",amount);
        bodyMap.put("stockLogId",stockLogId);

        HashMap<String, Object> argsMap = new HashMap<>();
        argsMap.put("itemId",itemId);
        argsMap.put("amount",amount);
        argsMap.put("userId",userId);
        argsMap.put("promoId",promoId);
        argsMap.put("stockLogId",stockLogId);

        //创建一条消息,前后2个massage都可能有坑，IDEA自动导包，导的是SUN里的Message。而非NQ里的Message
        Message message = new org.apache.rocketmq.common.message.Message(topicName,"increase",
                //将消息map转成JSON的string放进message中
                JSON.toJSON(bodyMap).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult sendResult = null;
            try {
                //将message通过生产者的send方法投放过去（投放给broker）
                //这里调用的是transactionMQ中的事务型send方法,
                // 它发送的是一个事务型消息，会存在2阶段提交，消息会被标记为prepare(准备)
                //同时本地会执行上面的executeLocalTransaction,也就是执行本地事务
                //传入的args类型的argsMap，args类型的这个值会被本地事务接收到
                sendResult = transactionMQProducer.sendMessageInTransaction(message,argsMap);
                System.out.println(sendResult);
            } catch (MQClientException e) {
                e.printStackTrace();
                System.out.println("消息客户端异常");
                return false;
            }
            //如果本地事务返回的是Rollback
            if (sendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE){
                return false;
                //如果本地事务返回的是true
            }else if (sendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE){
                return true;
            }else {
                //即出现 un know时返回false,也就是下单失败
                return false;
            }
    }

}
