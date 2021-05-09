package com.secKillingProject.service;

import com.secKillingProject.service.model.PromoModel;

/**
 * @author fucker
 * 秒杀活动
 */
public interface PromoService {
    /**获取即将或者正在进行的秒杀活动id
     * itemId 商品的id
     * PromoModel 活动对象
     * */
    PromoModel getPromoByItemId(Integer itemId);

    /**活动发布时，将DB中的商品信息写入Redis
     *
     * 发布活动
     * */
    void publishPromo(Integer promoId);

    /**根据获得id,生成秒杀令牌
     * */
    String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId);

}
