package com.secKillingProject.service;

import com.secKillingProject.error.BusinessException;
import com.secKillingProject.service.model.ItemModel;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;

import java.util.List;

public interface ItemService {
    /*最基础的和商品信息相关的接口，即提供商品信息相关的增删改查*/

    /**创建商品*/
    ItemModel createItem(ItemModel itemModel) throws BusinessException;

    /**商品列表浏览*/
    List<ItemModel> listItem();

    /**商品详情浏览*/
    ItemModel getItemById(Integer id);

    /**库存扣减!!!这是属于商品行为的一部分，因此放在这个service中*/
    boolean decreaseStock(Integer itemId,Integer amount) throws BusinessException, InterruptedException, RemotingException, MQClientException, MQBrokerException;
    /**库存扣减成功，对应的商品销量增加*/
    void increaseSales(Integer itemId,Integer amount) throws BusinessException;

//    优化1，验证item及 promo是否有效（itemModel以及promoModel的缓存模型）
//    缓存模型，即是存储在缓存中的模型。本来要后面查询到再放进redis中，现在把对应的模型数据提前放进缓存中
    ItemModel getItemByIdInCache(Integer id);

    /**初始化库存流水*/
    String initStockLog(Integer itemId,Integer amount);


}
