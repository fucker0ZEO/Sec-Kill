package com.secKillingProject.service.impl;

import com.secKillingProject.dao.OrderDOMapper;
import com.secKillingProject.dao.SequenceDOMapper;
import com.secKillingProject.dao.StockLogDOMapper;
import com.secKillingProject.dataObject.OrderDO;
import com.secKillingProject.dataObject.SequenceDO;
import com.secKillingProject.dataObject.StockLogDO;
import com.secKillingProject.error.BusinessException;
import com.secKillingProject.error.EmBusinessError;
import com.secKillingProject.service.OrderService;
import com.secKillingProject.service.model.ItemModel;
import com.secKillingProject.service.model.OrderModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private ItemServiceImpl itemService;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private OrderDOMapper orderDOMapper;

//    引入sequence对象
    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    //引入库存流水对象
    @Autowired
    private StockLogDOMapper stockLogDOMapper;


    @Override
    @Transactional
    /**看上去只是一个创建订单，实际上这个方法内是减库存+创建订单+增加销量 */
    public OrderModel createOrder(Integer userId, Integer itemId, Integer promoId,Integer amount,String stockLogId) throws BusinessException {
        /*整个校验逻辑由查DB变成了查Redis.Model来自于redis*/
//        校验下单状态，下单的商品是否存在，用户是否合法，购买数量是否正确。【对数据库的写操作，基本都需要这一步的校验】
        /*根据商品id查询到对应的商品模型对象*/
//        ItemModel itemModel = itemService.getItemById(itemId);
        //根据itemid到redis拿到Model
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
//
////        校验商品是否存在，这里也就是可以处理-1，-2，-3，-4这类产生缓存穿透问题的非法数据
        if(itemModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
        }


//        /*同样的由查DB获取userModel变成了查redis获取userMoel的缓存模型*/
//        /*根据用户id查询到对应的用户模型对象*/
////        UserModel userModel = userService.getUserById(userId);
//        UserModel userModel = userService.getUserByIdInCache(userId);
//
////        校验用户是否存在
//        if(userModel == null){
//            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"用户不存在");
//        }


//        校验购买的数量信息
        if(amount <=0||amount > 99){
          throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"数量信息不正确");
        }

//        System.out.println("纯粹的promoId"+promoId);
//        System.out.println("promoId的value"+promoId.intValue());
//        System.out.println("itemModel通过ProModel属性获取的Id值"+ itemModel.getPromoModel().getId());
//        校验活动信息
//        if (promoId !=null){
////            1.校验对应的活动是否存在这个适用商品 。这里传参坑，前面的promoId，应该是获取到promo表的Id值，结果获取到的是表中的item_id
////            promoId是前端传过来的值，去controller里查
//            //看上去这是一步内存操作，但是因为是聚合模型，前面拿到商品信息是一次SQL，现在拿的是和商品信息相关联的活动信息表的id。
//            // 刚才那条查商品表的SQL是做不到的，需要 一条SQL
//            if (promoId.intValue() != itemModel.getPromoModel().getId()){
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
////           2.校验活动是否正在进行中
//            }else if (itemModel.getPromoModel().getStatus().intValue() !=2){
//                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动还未开始");
//
//            }
//
//        }




//        落单减库存。这里不考虑支付减库存(实现麻烦....支付模块和砍单。企业级的实现中做的是支付减库存)
        boolean result = itemService.decreaseStock(itemId, amount);
//      返回值是false代表减库存失败
//      订单生成和库存终究是2步操作，对于DB来说是两条SQL，
//      两个操作被绑定为一个事务中，看着像是一个操作，但是终究可以分开
//        类似的还有转账，对于DB来说，实际上是2条SQL，A库中的金额扣减，B库中的金额增加，
//        按照普通人的逻辑思维是A库中的金额扣减后，B库中的金额才能增加
//        但是对于程序而言，没有这种顺序，只要保证它们同时成功，同时失败即可
//        对于人来说是，扣减库存，生成订单，但是对于程序来说可以是生成订单，再扣减库存，只要保证它们能同时成功/失败即可
//        或者说，在原子性中，无顺序
        if(!result){
            /**这里是生成订单的时候返回库存不足*/
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);

        }



//        订单入库,传入订单数据，增加一行order_info表的记录
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
//        获取到活动的id
        orderModel.setPromoId(promoId);

        //如果promoId不为null，那么price就应该取活动价格
        if(promoId !=null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else {
            //        通过Model的getPrice获取普通单价
            orderModel.setItemPrice(itemModel.getPrice());
        }
//        计算整个订单的金额,单价×数量
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));


        //生成交易流水号，即订单号
        orderModel.setId(generateOrderNot());
//        传入orderModel，调用转化方法，输出orderDO
        OrderDO orderDO = convertFromOrderModel(orderModel);
//        通过orderDOMapper的insertSelective方法，向order_info中写入数据,即生成的订单写回数据库
        orderDOMapper.insertSelective(orderDO);

//      根据itemId和购买数量俩增加销量
        itemService.increaseSales(itemId,amount);

        //根据库存流水id，查具体的库存流水记录
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if (stockLogDO == null){
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        //设置库存流水状态为成功,即为2，下单成功和库存流水在同一个事务中
        stockLogDO.setStatus(2);
        //修改库存流水状态后写回数据库
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);


//         orderModel返回前端

        return orderModel;
    }

    /**OrderModel转化为OrderDO的工具方法*/
    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if (orderModel == null){
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel,orderDO);
        return orderDO;
    }

    /**生成订单号
     * 订单号一般是16位
     * 前8位是年月日，不仅让订单信息可以看出是什么时间生成的，
     * 还有一个比较好的时间维度的归档点。即后面可以实现xx日以前的数据进行归档
     * 中6位是自增订单序列号，保证订单号的不重复
     * 后2位是分库分表号，也就是代表这个订单最后会落到哪个库哪个表，
     * 例如通过 用户id % 100 后得到的2位数的模，就可以实现
     * */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private String generateOrderNot(){
        //订单号16位
        StringBuilder stringBuilder = new StringBuilder();
        //8位的当期时间戳生成，年月日
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        //时间戳传入订单
        stringBuilder.append(nowDate);

        /*中间6位为自增序列,通过sequence_info表传入数据。sequence表的初始值为0，
        每次查询sequence表加一个步长，来作为随机值*/

        int sequence =0;

        /*传入order_info,获取当前sequence,即sequenceDO对象*/
        SequenceDO sequenceDO = sequenceDOMapper.getSequence("order_info");
        /*拿到该记录中的CurrentValue，即当前sequence的值(步长),后面做拼接用*/
        sequence = sequenceDO.getCurrentValue();
        /*将sequence的值加上一个步长在写回DO中*/
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        /*通过DO更新到数据库中*/
        sequenceDOMapper.updateByPrimaryKey(sequenceDO);

        //sequence做拼接，先把它转为string,再for循环补0后拼接
        String sequenceStr = String.valueOf(sequence);
        for (int i =0;i<6-sequenceStr.length();i++){
            stringBuilder.append(0);
        }
        stringBuilder.append(sequenceStr);


        //最后2位的分库分表位，暂时写死
        stringBuilder.append("00");

        return stringBuilder.toString();

    }


}

