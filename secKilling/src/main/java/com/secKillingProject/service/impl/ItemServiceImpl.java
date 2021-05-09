package com.secKillingProject.service.impl;

import com.secKillingProject.dao.ItemDOMapper;
import com.secKillingProject.dao.ItemStockDOMapper;
import com.secKillingProject.dao.StockLogDOMapper;
import com.secKillingProject.dataObject.ItemDO;
import com.secKillingProject.dataObject.ItemStockDO;
import com.secKillingProject.dataObject.StockLogDO;
import com.secKillingProject.error.BusinessException;
import com.secKillingProject.error.EmBusinessError;
import com.secKillingProject.mq.MqProducer;
import com.secKillingProject.service.ItemService;
import com.secKillingProject.service.PromoService;
import com.secKillingProject.service.model.ItemModel;
import com.secKillingProject.service.model.PromoModel;
import com.secKillingProject.validator.ValidationResult;
import com.secKillingProject.validator.ValidatorImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ItemServiceImpl implements ItemService {
    @Autowired
    private ValidatorImpl validator;

    @Autowired
    private ItemDOMapper itemDOMapper;

    @Autowired
    private ItemStockDOMapper itemStockDOMapper;



/**注入promoService*/
    @Autowired
    private PromoService promoService;

    /**注入redis,后面实现商品信息和活动信息的缓存预热
     * 或者说缓存模型*/
    @Autowired
    private RedisTemplate redisTemplate;

    /**注入MqProducer，把消息给MQ*/
    @Autowired
    private MqProducer mqProducer;
    /**注入对应的StackLogDOMapper*/
    @Autowired
    private StockLogDOMapper stockLogDOMapper;

    //Model转化为DO的前置
    private ItemDO convertItemDOFromItemModel(ItemModel itemModel){
        if (itemModel == null){
            return null;
        }
        ItemDO itemDO = new ItemDO();
        BeanUtils.copyProperties(itemModel,itemDO);
//       itemDO.setPrice(BigDecimal.valueOf(itemModel.getPrice().doubleValue()));
        return itemDO;
    }
    //写入数据库的前置
    private ItemStockDO convertItemStockDOFromItemModel(ItemModel itemModel){
        if (itemModel == null){
            return null;
        }
        ItemStockDO itemStockDO = new ItemStockDO();
        itemStockDO.setItemId(itemModel.getId());
        itemStockDO.setStock(itemModel.getStock());
        return itemStockDO;
    }


    @Override
    @Transactional
    public ItemModel createItem(ItemModel itemModel) throws BusinessException {
        /**创建Item信息，但是要先搞定校验入参*/
        ValidationResult result = validator.validate(itemModel);
        if (result.isHasErrors()){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }

        /**转化itemModel变成itemDO*/
        ItemDO itemDO = this.convertItemDOFromItemModel(itemModel);


        /**写入数据库*/
        itemDOMapper.insertSelective(itemDO);
        itemModel.setId(itemDO.getId());
        ItemStockDO itemStockDO = this.convertItemStockDOFromItemModel(itemModel);

        /**入库*/
        itemStockDOMapper.insertSelective(itemStockDO);

        /**返回创建完成的对象,通过service层的getItemById，返回对象*/
        return this.getItemById(itemModel.getId());
    }



    @Override
    public List<ItemModel> listItem() {
        List<ItemDO> itemDOList = itemDOMapper.listItem();
        List<ItemModel> itemModelList = itemDOList.stream().map(itemDO -> {
            ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());
            ItemModel itemModel = this.convertModelFromDataObject(itemDO, itemStockDO);
            return itemModel;
        }).collect(Collectors.toList());
        return itemModelList;
    }

    @Override
    public ItemModel getItemById(Integer id) {

        ItemDO itemDO = itemDOMapper.selectByPrimaryKey(id);
//
        if (itemDO == null){
//            这里DO为null就直接返回了且无报错。。。。
            return null;
        }
        //通过item表中的id赋值给Stock表中的item_id查询出库存数量
        ItemStockDO itemStockDO = itemStockDOMapper.selectByItemId(itemDO.getId());

        //将dataObject转化为Model。之前是从Model转化为DO，现在又从DO转化为Model
        ItemModel itemModel = this.convertModelFromDataObject(itemDO, itemStockDO);

        //获取item中的处于秒杀活动中的商品的信息，
        // 通过商品id查promo表，获取对应的秒杀活动记录。即获取到promoModel

        PromoModel promoModel = promoService.getPromoByItemId(itemModel.getId());
        System.out.println("最初的promoModel的值"+promoModel);
        System.out.println("promoService的值"+promoService);
        System.out.println("itemModel获取id"+itemModel.getId());
        //商品为null，代表这个商品没有参与秒杀活动。
        // 如果不为null，且状态值不为3，表示该商品在秒杀活动中（且活动未结束）
        if (promoModel != null && promoModel.getStatus().intValue()!= 3){
            //将有效的秒杀商品设置在活动中。即itemModel的promoModel属性上得到对应的值
            itemModel.setPromoModel(promoModel);

        }

        return itemModel;
    }

    //    dataObject转化为Model的工具类。convert，从...转化
    private ItemModel convertModelFromDataObject(ItemDO itemDO,ItemStockDO itemStockDO){
        ItemModel itemModel = new ItemModel();
        BeanUtils.copyProperties(itemDO,itemModel);

//        itemModel.setPrice(new BigDecimal(String.valueOf(itemDO.getPrice())));
        itemModel.setStock(itemStockDO.getStock());

        return itemModel;
    }



    @Override
    @Transactional
    public boolean decreaseStock(Integer itemId, Integer amount) throws BusinessException {
/**       用更新的SQL语句解决扣减库存。
 * item表大部分都还会用于查询，而库存表基本是写表，
 * 之前分开库存表和商品表就相当于做了读写表的分离
 *对库存进行操作时，会加锁在单行库存记录上，但因为之前就分表了行锁不会影响item表的性能
 * 后面性能调优就可以对写表进行业务降级
 * 整体思路是把频繁写的字段拆分出来，甚至是做成单表。减少其他字段的压力，
 * 且最坏的条件下能对单表做业务降级，哪怕这个字段的信息用不了，
 * 也不影响其他部分的可用性
 * 这也就是分库分表
 * */

/** 传入itemId,amount，执行更新库存的SQL，返回影响的条目数。
 * 影响的条目数有2个结果。0和1，前者代表无影响条目，即失败
 * 后者代表影响条目为 1，即更新成功
 * 可以通过这种方式 进行判断，
 * 少写一条SQL
 * (另一种做法是用amount和查询出的数据库中的stock进行比较，
 * 够扣则执行扣减库存的操作，这样就有2条SQL了。
 * 高并发下SQL的执行次数要足够少)，
 * 提高了性能
 *
 * 对之前的减库存的方式进行优化
 * increment代表增量，k是itemId，V是amount.intValue()*-1,即数量变成原来-1
 * 返回值，就是完成操作后剩下的数值，即库存-1
 */
//        int affectedRow = itemStockDOMapper.decreaseStock(itemId, amount);
        Long result = redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue() * -1);

//        if(affectedRow > 0){
//        剩下的库存大于等于0，就算更新库存成功。Redis中库存不足，直接返回秒杀失败
        if (result >= 0) {
            //更新库存成功，告诉MQ，让MQ去减数据库的库存.
            /**将itemId和amount传给MQ，所谓的消息即bodyMap，
             * 即itemId和amount
             * 生产者拿到itemId和amount，消费者自然就会消费消息*/
//           boolean mqResult= mqProducer.asyncReduceStock(itemId, amount);

            //Boolean 的mqResult 为false就代表MQ更新库存失败
//           if (!mqResult){
//               /**出错，全部return false,
//                * 且把库存加回去
//                * (原值减1就是扣减库存，原值就是把库存加回去。
//                * 这里的amount是之前查Redis得到的值，
//                * 没有第二次查Redis,因此值还相当于是原值，和MySQL的保持一致)*/
//               redisTemplate.opsForValue().increment("promo_item_stock_" + itemId,amount.intValue());
//               return false;
            return true;
        } else if (result == 0) {
            //打上库存售完标识.这条key存在且值为true即已售完
            redisTemplate.opsForValue().set("promo_item_stock_invalid_"+itemId,"true");

            //更新库存成功
            return true;
        }else {
            //库存小于0，更新库存失败。库存补回去
            redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue());
            return false;
        }
//        给出返回值true和false分别代表减库存失败和成功

    }

/** 落单成功，即增加对应数量的销量*/
    @Override
    @Transactional
    public void increaseSales(Integer itemId, Integer amount) {
        itemDOMapper.increaseSales(itemId,amount);
    }

/** 实现缓存模型，即将Item模型提前放入缓存中
 * Item模型中自然有promoModel这个属性，因此promoModel也就放进了缓存中
 * 直接先判断对应的itemModel的缓存是否为null，为null就从DB中取
 * */
    @Override
    public ItemModel getItemByIdInCache(Integer id) {
        //根据id查询对应的item_Model
       ItemModel itemModel= (ItemModel) redisTemplate.opsForValue().get("item_validate_"+id);
        //判断item_Model是否存在
        if (itemModel == null){
            //如果缓存中的itemModel不存在，就调用本类中的getItemById从数据库中取.
            itemModel = this.getItemById(id);
            //取到的值放进redis中。key为"item_validate_"+id，value为itemModel，过期时间为？？？（这种预热数据一般过期时间都是超长的）
            redisTemplate.opsForValue().set("item_validate_"+id,itemModel);
            //过期时间是10分钟，这个过期时间太短了。。。后面出现集中过期就是缓存雪崩
            redisTemplate.expire("item_validate_"+id,10, TimeUnit.MINUTES);
        }
        //返回对应的itemModel,这个就已经是缓存中的itemModel了
        return itemModel;
    }

    /**初始化库存流水，将状态设置为准备开始冻结库存，
     * 并提交对应的事务，使DB内有对应的stockLog生成
     * stockLog生成后再去produce，
     * 之后check Local Transaction内就会有对应的stockLog的id
     * 即可最终到下单状态是成功还是失败
     * status 1表示初始状态，2表示下单扣减库存成功，3表示下单回滚*/
    @Override
    @Transactional
    public String initStockLog(Integer itemId, Integer amount) {
        //初始化，即创建对应的StockLogDO对象，通过set方法向DB内写数据
        StockLogDO stockLogDO = new StockLogDO();
        stockLogDO.setItemId(itemId);
        stockLogDO.setAmount(amount);

        //UUID随机生成主键并且set进DB中,-替换成空字符串
        stockLogDO.setStockLogId(UUID.randomUUID().toString().replace("-",""));
        //默认初始状态为1，2为成功，3为回滚
        stockLogDO.setStatus(1);
        //将stockLogDO写入DB中
        stockLogDOMapper.insertSelective(stockLogDO);
        //返回库存流水的id，调用时根据库存流水的id就可以拿到具体的流水记录
        return stockLogDO.getStockLogId();
    }

}
