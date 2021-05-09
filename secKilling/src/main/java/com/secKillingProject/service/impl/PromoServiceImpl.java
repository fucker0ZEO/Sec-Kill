package com.secKillingProject.service.impl;

import com.secKillingProject.dao.PromoDOMapper;
import com.secKillingProject.dataObject.PromoDO;
import com.secKillingProject.service.ItemService;
import com.secKillingProject.service.PromoService;
import com.secKillingProject.service.UserService;
import com.secKillingProject.service.model.ItemModel;
import com.secKillingProject.service.model.PromoModel;
import com.secKillingProject.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author fucker
 */
@Service
public class PromoServiceImpl implements PromoService {
    /**注入对应的DO*/
    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    /**注入对应的涩service*/
    private ItemService itemService;

    @Autowired
    /**注入Redis*/
    private RedisTemplate redisTemplate;

    /**注入UserService*/
    @Autowired
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
//    DO转化为Model。Model的属性获得从数据库中查询出的值
        PromoModel promoModel = convertFromDataObject(promoDO);
//        判断当前时间，秒杀活动是否即将开始或者正在进行
        /*model判空*/
        if (promoModel ==null){
            return null;
        }
//        isAfterNow方法，比较时间的大小。秒杀时间比现在还晚，即未开始
        if (promoModel.getStartDate().isAfterNow()){
            //状态值为1
            promoModel.setStatus(1);
            /*isBeforeNow方法，如果结束时间比现在还要前面，即在现在以前就结束，已结束*/
        }else if (promoModel.getEndDate().isBeforeNow()){
            //状态值为3
            promoModel.setStatus(3);
        }else{
            //其余情况就是正在进行中。状态值为2
            promoModel.setStatus(2);
        }

        return promoModel;
    }

    /**实现发布活动
     * 而发布活动时，最重要的就是将ItemIdModel写入缓存。
     * 先通过活动id获得PromoDO
     * 然后根据proMoDO拿得ItemID
     * 然后判断是否为NUll，然后写入缓存
     * * */
    @Override
    public void publishPromo(Integer promoId) {
        //通过活动id拿到promoDO
       PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
       //通过活动DO，拿到ItemId,并判断是否存在这个活动。为null或者值为0都是不存在
        if(promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0){
            return;
        }
        //通过itemId拿到itemModel
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());
        //拿到ItemStock，，并将其写入Redis中
        //key为"item_"+itemModel.getId(),即item_id
        // value为itemModel.getStock(),socket
        redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(),itemModel.getStock());
        //过期时间应该设置为一个较长的时间

        //将大闸的限制数字设到redis内,即最大能有库存数量的5倍的用户获取到秒杀的大闸
        redisTemplate.opsForValue().set("promo_door_count_"+promoId,itemModel.getStock().intValue() * 5);

    }

    /**根据活动id，生成秒杀令牌*/
    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) {
        //拿到对应的promoModel
        //根据秒杀id，获取对应秒杀活动model
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
//    DO转化为Model。Model的属性获得从数据库中查询出的值
        PromoModel promoModel = convertFromDataObject(promoDO);
//        判断当前时间，秒杀活动是否即将开始或者正在进行
        /*model判空*/
        if (promoModel ==null){
            return null;
        }

        //hasKey判断该key是否存在，存在即已售完,则直接返回下单失败
        if (redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)){
            return null;
        }


//        isAfterNow方法，比较时间的大小。秒杀时间比现在还晚，即未开始
        if (promoModel.getStartDate().isAfterNow()){
            //状态值为1
            promoModel.setStatus(1);
            /*isBeforeNow方法，如果结束时间比现在还要前面，即在现在以前就结束，已结束*/
        }else if (promoModel.getEndDate().isBeforeNow()){
            //状态值为3
            promoModel.setStatus(3);
        }else{
            //其余情况就是正在进行中。状态值为2
            promoModel.setStatus(2);
        }
        //不为2，即不在活动中，那就不允许生成秒杀令牌
        if (promoModel.getStatus().intValue() != 2){
            return null;
        }
        //判断item信息是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            return null;
        }
        //判断用户信息是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel == null){
            return null;
        }
        //获取秒杀大闸的count数量
        Long result = redisTemplate.opsForValue().increment("promo_door_count_" + promoId, -1);
        //大闸中令牌数量小于0，不能生成秒杀令牌
        if (result <0){
            return null;
        }

        //满足上面一系列判断则可以生成令牌，使用UUID生成,将-换成空字符串
        String token = UUID.randomUUID().toString().replace("-","");
        //秒杀令牌存到Redis内。用userID,PromoId,itemID三个维度确定Token
        redisTemplate.opsForValue().set("promo_token_"+promoId+"_user_"+userId+"_item_"+itemId,token);
        //令牌的过期时间为5分钟
        redisTemplate.expire("promo_token_"+promoId+"_user_"+userId+"_item_"+itemId,5, TimeUnit.MINUTES);
        return token;
    }

    //    DO转化为Model的工具方法
    private PromoModel convertFromDataObject(PromoDO promoDO){
        //判空，copy转换，返回三部曲
        if (promoDO == null){
            return null;
        }
        PromoModel promoModel =new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
//        这个2时间类是用的Java默认的时间类，需要转化一下
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }
}
