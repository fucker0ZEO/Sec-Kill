package com.secKillingProject.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.secKillingProject.error.BusinessException;
import com.secKillingProject.error.EmBusinessError;
import com.secKillingProject.mq.MqProducer;
import com.secKillingProject.response.CommonReturnType;
import com.secKillingProject.service.ItemService;
import com.secKillingProject.service.OrderService;
import com.secKillingProject.service.PromoService;
import com.secKillingProject.service.model.UserModel;
import com.secKillingProject.util.CodeUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @author fucker
 */
@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    /**引入RedisTemplate 验证token*/
    @Autowired
    private RedisTemplate redisTemplate;

    /**注入mqProduce*/
    @Autowired
    private MqProducer mqProducer;
    /**注入ItemService*/
    @Autowired
    private ItemService itemService;
    /**注入PromService拿秒杀令牌*/
    @Autowired
    private PromoService promoService;


    private ExecutorService executorService;
    //限流器
    private RateLimiter orderCreateRateLimiter;
    @PostConstruct
    public void init(){
        //创建一个20工作线程的线程池
        executorService = Executors.newFixedThreadPool(20);

        //初始化，限流器，允许1秒通过300个,两台下单服务器700多的流量
        orderCreateRateLimiter = RateLimiter.create(300);
    }

    /**生成验证码*/
    @RequestMapping(value = "/generateverifycode", method = {RequestMethod.GET,RequestMethod.POST})
    @ResponseBody
    public void  generateverifycode(HttpServletResponse response) throws BusinessException, IOException {
        //验证用户的登录状态
        //根据Token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)){
            //token如果为null
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登录，不能生成验证码");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登陆，不能生成验证码");
        }
        /**拿到装验证码的Map*/
        Map<String, Object> map = CodeUtil.generateCodeAndPic();
        //将用户id和验证码做绑定set进Redis中
        redisTemplate.opsForValue().set("verify_code_"+userModel.getId(),map.get("code"));
        //过期时间10分钟
        redisTemplate.expire("verify_code_"+userModel.getId(),10,TimeUnit.MINUTES);
        //通过IO写入到http response的输出流中
        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
    }




    /**生成秒杀令牌*/
    @RequestMapping(value = "/generatetoken", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generatetoken(@RequestParam(name="itemId")Integer itemId,
                                        @RequestParam(name = "promoId")Integer promoId,
                                          @RequestParam(name = "verifyCode")String verifyCode) throws BusinessException {


        //根据Token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if (StringUtils.isEmpty(token)){
            //token如果为null
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登录，不能下单");
        }
        //如果token存在则在redis中根据token查询用户的登录信息。存在就代表这个token是这个用户的，不存在大概率是过期了
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户登录已过期，请重新登录");
        }

        //查询Redis获取验证码
        String redisVerifyCode = (String) redisTemplate.opsForValue().get("verify_code_" + userModel.getId());
        //
        if (StringUtils.isEmpty(redisVerifyCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"请求非法");
        }
        System.out.println("前端传来的验证码"+verifyCode);
        System.out.println("Redis中传进来的验证码"+redisVerifyCode);
        //比较查询出来的验证码和前端传进来的验证码
        if (!redisVerifyCode.equalsIgnoreCase(verifyCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"请求非法,验证码错误");

        }


        //r,传入活动ID，商品ID，用户ID
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());

        //秒杀令牌生成错误,拦截非法请求
        if (promoToken == null){
            System.out.println("userID为："+userModel.getId());
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"生成令牌失败");
        }

        //返回对应的结果
        return CommonReturnType.create(promoToken);
    }




    //封装下单请求
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name="itemId")Integer itemId,
                                        @RequestParam(name = "amount")Integer amount,
//                                          不传就认定为false
                                        @RequestParam(name = "promoId",required = false)Integer promoId,
                                        //这里也不一定是必传，不传认定为false。普通商品下单可以不用秒杀令牌
                                        @RequestParam(name = "promoToken",required = false)String promoToken) throws BusinessException {

//    重构为token    //获取用户的登录信息，登录信息是放在session中的
//        Boolean isLogin = (Boolean) httpServletRequest.getSession().getAttribute("IS_LOGIN");
////        System.out.println(isLogin.booleanValue()); 这个打印还不能乱加，加了就是未知错误10002，空指针异常
//        if (isLogin == null || !isLogin.booleanValue()){
//            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登录，不能下单");
//        }
//        //session中登录信息,即该用户对应的userModel
//        UserModel userModel = (UserModel) httpServletRequest.getSession().getAttribute("LOGIN_USER");

        //如果限流器返回失败
        if (!orderCreateRateLimiter.tryAcquire()){
            //即没有拿到对应的令牌
            throw new BusinessException(EmBusinessError.RATELIMIT);

        }



        //通过HttpRequest从前端获取到第一个token，即这个客户端的当前用户的token
        String token = httpServletRequest.getParameterMap().get("token")[0];
        //校验秒杀令牌是否正确

        if (StringUtils.isEmpty(token)){
            //token如果为null
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户还未登录，不能下单");
        }
        //如果token存在则在redis中根据token查询用户的登录信息。存在就代表这个token是这个用户的，不存在大概率是过期了
       UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if (userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN,"用户登录已过期，请重新登录");
       }
        //验证秒杀令牌是否正确
        if (promoToken != null){
            String inRedispromoToken = (String) redisTemplate.opsForValue().get("promo_token_"+promoId+"_user_"+userModel.getId()+"_item_"+itemId);
            if (inRedispromoToken == null){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"令牌校验失败");
            }
            if (!StringUtils.equals(promoToken,inRedispromoToken)){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"令牌校验失败");
            }
        }

//        System.out.println("传过去的活动信息具体值"+userModel.getId()+"***1"+itemId+"***2"+promoId+"***3"+amount);
//        OrderModel orderModel = orderService.createOrder(userModel.getId(),itemId,promoId,amount);
            
        //同步调用线程池的submit方法，拥塞窗口为20，容量为21亿的等待队列，用来队列泄洪
        Future<Object> future = executorService.submit(new Callable<Object>() {
            //将库存流水和异步减库存加入线程池的Call方法中
            @Override
            public Object call() throws Exception {
                //加入库存流水init状态,根据itemId和amount回滚
                String stockLogId = itemService.initStockLog(itemId,amount);


                //再调用MQ的事务型消息异步减库存，本地事务中会调用创建订单的方法.transactionAsyncReduceStock为异步事务扣减库存
                if (!mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount,stockLogId)){
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"下单失败");
                }
                return null;
            }
        });

        try {
            //拿到future对象后等待它返回
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        return CommonReturnType.create(null);
    }
}
