package com.secKillingProject.controller.viewObject;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItemVO {
    private Integer id;
    /**商品名称描述*/

    private String title;
    /**任意大小且精度完全准确的浮点数用这个，不用float*/

    private BigDecimal price;
    /**商品库存*/

    private Integer stock;
    /**商品的文描*/

    private String description;
    /**商品的销量。销量不是前台录入进来，是统计来的。因此非入参*/
    private Integer sales;
    /**商品描述图片的URL*/

    private String imgUrl;

    /**模型聚合后，它也有了promoModel中的属性*/

    /**商品是否在秒杀活动中，即对应的状态
     * 0，不在活动中
     * 1，未开始
     * 2，进行中
     * 3. 已结束？3已经在service中被排除掉了，不为3才这一步VO返回这个值回前端
     * */
    private Integer promoStatus;

    /**秒杀活动价格*/
    private BigDecimal promoPrice;

    /**秒杀活动Id*/
    private Integer promoId;

    /**秒杀活动开始时间*/
    private String startDate;


}
