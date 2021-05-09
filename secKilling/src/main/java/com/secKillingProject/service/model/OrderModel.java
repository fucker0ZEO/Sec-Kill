package com.secKillingProject.service.model;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**用户下单的交易模型
 * implements Serializable 实现Serializable这个自动序列化接口
 * */
@Data
public class OrderModel implements Serializable {
    /**id这里采用的是String类型，因为企业级的订单id是含义意义的字符串
     * 2021033100012528
     * */
    private String id;
    /**购买用户的id*/
    private Integer userId;
/**购买商品的id*/
    private Integer itemId;
   /**购买的数量*/
    private Integer amount;
    /**冗余一个字段，购买商品的单价.
     * 若promoId非空，则表示秒杀商品的价格*/
    private BigDecimal itemPrice;
/**    购买的金额
 * 若promoId非空，则表示秒杀商品的金额*/
    private BigDecimal orderPrice;

    /**若非空，表示以秒杀方式下单*/
    private Integer promoId;

    //落单减库存和支付减库存。无法保证支付后对应的库存还有(因为时间比较有限，可能其他人先支付后，库存为0了)
//    用户一旦支付成功就必然要保证它能够成功购买，而支付的时候可能库存已经为0.这个时候就存在超卖问题。不然就只能退款，这非常影响用户体验（支付成功却退款了）
//    同时如果仓库中还有部分冗余的货，超卖一点是能够接受的
//    而落单减库存，则可能会拍卖下后，不付款，等着订单自动超时关单，如果这样的恶意操作过多，就会存在大量的少卖
//    而需求分析中前端流程图中的先验证用户是否有资格购买，后减库存。也是为了避免少卖。(减库存后再校验资格就会出现砍单+库存回流)
//    而商家为了让用户有紧迫感，可以接受部分超卖的风险，避免自身少卖。毕竟囤货和库存才是供应链要命的。。。
//    当然，如果秒杀活动更倾向于营销（帅猴），那就是可以接受少卖而不能接受超卖....

}
