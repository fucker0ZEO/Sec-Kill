package com.secKillingProject.service.model;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class ItemModel implements Serializable {
    /**id,名称描述（一般都是名称+部分描述放在一起显示的）,图片，库存，原价，秒杀价，
     * 例如：高端职业装连衣裙酒店前台时尚气质女神范美容院工作服女空姐制服 这个就是名称描述
     * 因此没有用name来描述，而是用title
     * 这里有库存,也有销量
     * 这里还有一个文描，即大段文字描述
     * @NotBlank 用在string上，校验空字符串和null
     * @NotNull 用在int等数值型上，校验非0
     * @Min 校验最小值，max则相反
     * implements Serializable 实现Serializable这个自动序列化接口
     * */
    private Integer id;
    /**商品名称描述*/
    @NotBlank(message = "商品名称不能为空")
    private String title;
    /**任意大小且精度完全准确的浮点数用这个，不用float*/
    @NotNull(message = "商品价格不能为空")
    @Min(value = 0,message = "商品不免费")
    private BigDecimal price;
    /**商品库存*/
    @NotNull(message = "库存不能不填")
    private Integer stock;
    /**商品的文描*/
    @NotBlank(message = "商品必须要有描述")
    private String description;
    /**商品的销量。销量不是前台录入进来，是统计来的。因此非入参*/
    private Integer sales;
    /**商品描述图片的URL*/
    @NotBlank(message = "图片信息不能为空")
    private String imgUrl;

    /**当商品被赋予秒杀属性时，它就被聚合成了秒杀商品，
     * 自然也就有了秒杀活动 这个属性。
     * 即promoModel变成了商品的一个属性
     * 使用聚合模型，即Java中的嵌套，
     * 一个模型可以变成其他模型的一个属性
     *
     * 使用promoModel，如果PromoModel不为空，
     * 则表示item(中)拥有还未结束的秒杀活动
     * */
    private PromoModel promoModel;


}
