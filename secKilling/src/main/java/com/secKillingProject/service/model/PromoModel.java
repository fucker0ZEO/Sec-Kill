package com.secKillingProject.service.model;

import lombok.Data;
import org.joda.time.DateTime;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author fucker
 * 秒杀活动模型
 * promo，英文意思为营销
 * implements Serializable 实现Serializable这个自动序列化接口
 */
@Data
public class PromoModel implements Serializable {
    private Integer id;

    /**秒杀活动状态，为1表示未开始，为2表示进行中，为3表示已结束*/
    private Integer status;

/**  秒杀活动名称*/
    private String promoName;

/**    秒杀活动的开始时间,使用joda-time这个依赖包提供的时间类，
 * 而非Java本身自带的时间类，自带的坑*/
    private DateTime startDate;

    /**秒杀结束时间*/
    private DateTime endDate;

    /**秒杀活动的适用商品的id*/
    private Integer itemId;

    /**秒杀活动的商品价格*/
    private BigDecimal promoItemPrice;


}
