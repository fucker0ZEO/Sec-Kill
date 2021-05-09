package com.secKillingProject.service;

import com.secKillingProject.error.BusinessException;
import com.secKillingProject.service.model.OrderModel;

/**
 * @author fucker
 * 用来处理订单交易
 */
public interface OrderService {

    /**userId,下单用户的id
     * itemId，订单中商品的id
     * amount,订单金额
     * 1.通过签到URL上传过来秒杀活动ID，
     * 然后下单接口内校验对应商品且活动已开始
     *
     * 2.直接在下单接口内判断对应的商品是否存在秒杀活动，
     * 若存在进行中的，则以秒杀价格下单
     * 用第1种，便于模型的扩展性
     * */
    OrderModel createOrder(Integer userId,Integer itemId,Integer promoId,Integer amount,String stockLogId) throws BusinessException;
}
