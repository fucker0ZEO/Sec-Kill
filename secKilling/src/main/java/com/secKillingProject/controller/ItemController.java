package com.secKillingProject.controller;

import com.secKillingProject.controller.viewObject.ItemVO;
import com.secKillingProject.error.BusinessException;
import com.secKillingProject.response.CommonReturnType;
import com.secKillingProject.service.CacheService;
import com.secKillingProject.service.ItemService;
import com.secKillingProject.service.PromoService;
import com.secKillingProject.service.model.ItemModel;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller("/item")
@RequestMapping("/item")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
//@CrossOrigin(allowCredentials = "true",allowedHeaders = "*")
public class ItemController extends BaseController{

    @Autowired
    private ItemService itemService;

    @Autowired
    /**获取Redis模板，可以直接对Redis进行操作
     * */
    private RedisTemplate redisTemplate;
    /**获取本地Cache....没有注入进来，少注解，难怪不能跳转出空指针*/
    @Autowired
    private CacheService cacheService;

    @Autowired
    /**注入活动service，活动开始时，调用活动service完成活动的发布，即活动加载进缓存*/
    private PromoService promoService;

    //创建商品controller,sales和创建商品无关
    @RequestMapping(value = "/create",method = {RequestMethod.POST},consumes ={CONTENT_TYPE_FORMED})
    @ResponseBody
     public CommonReturnType createItem(@RequestParam(name = "title")String title,
                                       @RequestParam(name = "description")String description,
                                       @RequestParam(name = "price")BigDecimal price,
                                       @RequestParam(name = "stock")Integer stock,
                                       @RequestParam(name = "imgUrl")String imgUrl) throws BusinessException {
        //封装service请求用来创建商品。即将前端传过来的数据通过set方法传给对应的属性
        ItemModel itemModel = new ItemModel();
        itemModel.setTitle(title);
        itemModel.setDescription(description);
        itemModel.setPrice(price);
        itemModel.setStock(stock);
        itemModel.setImgUrl(imgUrl);

        //调用service的createItem方法，将model输入，输出对象
        ItemModel itemModelFroReturn = itemService.createItem(itemModel);


        //service层处理完后，经过controller返回给前端.调用转化工具方法，传入model拿到VO
        ItemVO itemVO = this.convertVOFromModel(itemModelFroReturn);

        //把VO返回给前端
        return CommonReturnType.create(itemVO);

    }

/**商品详情页浏览。浏览操作一般采用GET这个对服务端不发生任何改变的幂等操作*/
    @RequestMapping(value = "/get",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType getItem(@RequestParam(name = "id")Integer id){

        //多级缓存，第一步取的是本地缓存，本地没有再去Redis，Redis没有再去取数据库
        ItemModel itemModel =null;
        //id作为key，通过id去取本地缓存 原本是null和值分开，然后会直接出现空指针...这个是真的坑！！
//         itemModel = (ItemModel) cacheService.getFromCommonCache("item_"+id);
        itemModel = (ItemModel) cacheService.getFromCommonCache("item_"+id);
        //若本地缓存不存在则去Redis里取
        if(itemModel == null){
            //根据item_具体的id到redis内获取.直接controller层访问redis，不走service。减少路径
            itemModel = (ItemModel) redisTemplate.opsForValue().get("item_" + id);
            //若redis内不存在对应的itemModel,即Model为nulll,则访问下游的service
            if (itemModel == null){
                //根据ID查询到这行记录对应的Model对象,后面再把service层的model直接放进Redis中
                itemModel = itemService.getItemById(id);
                //并且将itemModel放进redis中.item_+id拼接起来的字符串为key,对应的itemModel为value
                redisTemplate.opsForValue().set("item_"+id,itemModel);
                /**缓存必然要有失效时间，不止是Redis容量问题，
                 * 更重要的是数据变更时，需要有清理缓存的机制
                 * 这里使用被动过期方式，定时10分钟过期
                 * 实际上惰性删除+定期删除作为缓存淘汰策略更好
                 * */
                redisTemplate.expire("item_"+id,10, TimeUnit.MINUTES);
            }
            //Redis中拿到了从数据库的值，给本地缓存也写入值。其实这个写入不是一步操作。第一次是给redis写入了值，但是本地缓存中依旧没有值，需要下一次再访问redis，才能给本地缓存中加上值
            cacheService.setCommonCache("item_"+id,itemModel);

        }



//        查询三连，结果是ID有值，Model，VO无值，那估计就是getItemById这个方法的锅了
//        System.out.println("id******"+id);
//        System.out.println("itemModel******"+itemModel);
//          model对象转化为VO
        ItemVO itemVO = convertVOFromModel(itemModel);
//        System.out.println("itemVO :********"+itemVO);
//          返回前端
        return CommonReturnType.create(itemVO);
    }
//        商品列表页面浏览
    @RequestMapping(value = "/list",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType listItem(){
        List<ItemModel> itemModelList = itemService.listItem();

//        使用stream流将list内的itemModel转化为ItemVo
        List<ItemVO> itemVOList = itemModelList.stream().map(itemModel -> {
            ItemVO itemVO = this.convertVOFromModel(itemModel);
            return itemVO;
        }).collect(Collectors.toList());
        return CommonReturnType.create(itemVOList);
    }

    /**发布活动，本来应该由专门的运营后台来完成的
     * 当活动发布时，系统会（调用活动service 即proMoService）完成那些事件？
     * 系统会调用service将活动商品库存加载到缓存中去
     * */
    @RequestMapping(value = "/publishpromo",method = {RequestMethod.GET})
    @ResponseBody
    /**活动发布时，传入的是活动id*/
    public CommonReturnType publishpromo(@RequestParam(name = "id")Integer id){
        /** 根据前端传过来的具体的活动id去完成这个活动的发布，
         * 即将这个活动中的商品加载进缓存*/
        promoService.publishPromo(id);
        /**这里因为没有做管理后台，特别是管理后台的前端。。给一个默认的返回值null即可*/
        return CommonReturnType.create(null);

    }









        /**model转化为VO的工具方法,传入Model返回VO
         * 为啥要做这种分层，然后再做转换？
         * 因此DO和VO很多时候是不同的，且很多时候需要用到聚合操作
         * Model到DO层，库存是通过dataObject层聚合出来的（业务逻辑上实现联表查询也就是聚合）
         *
         * 同时为了和前端交互方便，会把VO定义得比Model更大。例如会聚合上活动价格信息
         * */
        private ItemVO convertVOFromModel(ItemModel itemModel){
            if (itemModel == null){
                return null;
            }
            ItemVO itemVO =new ItemVO();
            //通过BeanUtils的copy方法实现
            BeanUtils.copyProperties(itemModel,itemVO);
            //商品的秒杀活动属性不为null，代表它是秒杀商品
            if (itemModel.getPromoModel() != null){
                /*有正在进行或即将进行的秒杀活动。
                 * 获取item属性中的promoModel属性中的status,itemId,StartDate,ItemPrice*/
                itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
//                  这里遇见了传参坑，传了promoModel的商品ID给活动活动id。实际上promoModel的ID才是promoId
//                itemVO.setPromoId(itemModel.getPromoModel().getItemId());
                itemVO.setPromoId(itemModel.getPromoModel().getId());
                itemVO.setStartDate(itemModel.getPromoModel().getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
                itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
            }else {
                itemVO.setPromoStatus(0);
            }
            System.out.println("VO:查看status的值"+itemVO.getPromoStatus());
//            System.out.println("Model：查看status的值"+itemModel.getPromoModel().getItemId());
            System.out.println("VO:查看ID的值"+itemVO.getPromoId());
            System.out.println("Model有值否"+itemModel);
            System.out.println("Model有PromoModel值"+itemModel.getPromoModel());
            return itemVO;
        }
}
