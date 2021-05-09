package com.secKillingProject.controller;

import com.secKillingProject.controller.viewObject.UserVO;
import com.secKillingProject.error.BusinessException;
import com.secKillingProject.error.EmBusinessError;
import com.secKillingProject.response.CommonReturnType;
import com.secKillingProject.service.UserService;
import com.secKillingProject.service.model.UserModel;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Encoder;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author fucker
 * @Controller("user") 用来被SpringBoot扫描，Controller的name就是user
 *@RequestMapping("/user") 通过/user这个路径被用户访问到
 *  @CrossOrigin 处理ajax跨域请求
 */
@Controller("user")
@RequestMapping("/user")
//@CrossOrigin(allowCredentials = "true",allowedHeaders = "*")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class UserController extends BaseController{

    @Autowired
    private UserService userService;

    /**注入HttpServletRequest对象，后面用它拿到session*/
    @Autowired
    private HttpServletRequest httpServletRequest;

    /**引入redisTemplate就可操作redis内嵌的bean*/
    @Autowired
    private RedisTemplate redisTemplate;

    /**用户登录接口实现*/
    @RequestMapping(value = "/login",method = {RequestMethod.POST},consumes ={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType login(@RequestParam(name="telphone")String telphone,
                                  @RequestParam(name = "password")String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        /*入参校验非空*/
        if(StringUtils.isEmpty(telphone)||StringUtils.isEmpty(password)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }
        /*用户登录服务，用来校验登录是否合法
        加密后的手机号和密码输入给service层,并接受输出的结果userModel*/
         UserModel userModel = userService.validateLogin(telphone,this.EncodeByMd5(password));


        //重构，修改成若用户登录验证成功后将对应的登录信息和登录凭证一起放到session中，再一起存入redis中

        //生成登录凭证token，采用UUID作为token,并顺便转为string类型，即UUID保证全局唯一
        String uuidToken = UUID.randomUUID().toString();
        //去掉原生UUID的_，这对URL不友好
        uuidToken =uuidToken.replace("-","");

        /**建立用户登录信息和登录凭证Token之间的联系 （需要redis的操作类）。
         * 就像是学生个人信息和学生证之间的一 一对应联系。
         * K是UUID，V是用户的登录信息即userModel*/
        redisTemplate.opsForValue().set(uuidToken,userModel);

        //设置key的超时时间为1个小时
        redisTemplate.expire(uuidToken,1, TimeUnit.HOURS);

        //将登录凭证（Cookie,Token）userModel加入用户登录成功的session内
//        this.httpServletRequest.getSession().setAttribute("IS_LOGIN",true);
//        this.httpServletRequest.getSession().setAttribute("LOGIN_USER",userModel);
//
//        System.out.println("检查Cookie"+this.httpServletRequest.getSession().getAttribute("IS_LOGIN"));



        //下方token，并返回给前端
        return CommonReturnType.create(uuidToken);
    }


    //用户注册接口实现,所需要的参数，手机号码
    /**bug +1
     * Resolved [org.springframework.web.bind.MissingServletRequestParameterException:
     * Required String parameter 'telpone' is not present]
     * 可以看到telphone是最前面的一个，其实可以改变顺序验证
     * 目前的情况是前端能够获取到telphone的值，但是没传回后端
     *参考了一下这个 https://www.imooc.com/qadetail/348135 它是OTP不一样 导致报错
     * 我的则是
     * @RequestParam(name="telpone")String telphone, telpone和telphone
     * 好的命名+拼写检查基本就不会有这个坑。。。。辣鸡命名就会被坑炸
     *
     * bug+2
     * Resolved [com.secKillingProject.error.BusinessException]  自定义的异常 表示短信验证码不正确
     * 验证码******null另一个验证码95978
     * getAttribute(telphone); 这个地方缺，session对象是存在的
     * 返回在此会话中绑定了指定名称的对象，如果该名称下未绑定任何对象，则返回null。
     * 分布式部署又遇见这个BUG了。。。
     * inSessionOtpCode为null，同样 的是 getAttribute(telphone) 这个方法获取不到值.上次是推测session被覆盖了
     * 设置完能获取到验证码，然后再获取就拿不到otpCode了。上次貌似是没有重启服务器，验证码和session分开了
     *
     * */

    @RequestMapping(value = "/register",method = {RequestMethod.POST},consumes ={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType register(
                        @RequestParam(name="telphone")String telphone,
                        @RequestParam(name="otpCode")String otpCode,
                        @RequestParam(name="name")String name,
                        @RequestParam(name="gender") Byte gender,
                        @RequestParam(name="age")Integer age,
                        @RequestParam(name="password")String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {

        //验证手机号和对应的OtpCode相符合。之前将手机号和验证码放在HttpSession中.

        String inSessionOtpCode = (String) this.httpServletRequest.getSession().getAttribute(telphone);
        System.out.println("再看是否为null"+inSessionOtpCode);
        System.out.println("req:******"+httpServletRequest);
        System.out.println("session:******"+httpServletRequest.getSession());
        System.out.println("telphonep:******"+telphone);
        System.out.println("验证码******"+inSessionOtpCode+"另一个验证码"+otpCode);
        //两个验证码进行比对。使用类库里的equals
        if(!com.alibaba.druid.util.StringUtils.equals(otpCode,inSessionOtpCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"短信验证码不符合");

        }


        //用户的注册流程
        UserModel userModel =new UserModel();
        userModel.setName(name);
        userModel.setGender(new Byte(String.valueOf(gender.intValue())));
        userModel.setAge(age);
        userModel.setTelphone(telphone);
        userModel.setRegisterMode("byphone");
        System.out.println("service的锅：xxxx"+userService);
        //MD5加密密码
        userModel.setEncrptPassword(this.EncodeByMd5(password));
        /**register方法拿到了userModel,即拿到了userDO.之后需要getId
         *
         * UserDO userDO = convertFromModel(userModel);
         * userDOMapper.insertSelective(userDO);
         * userModel.setId(userDO.getId());
         * 需要将自增ID取出来后赋值给对应的userModel
         * */
        userService.register(userModel);
        return CommonReturnType.create(null);
    }




// 自己实现MD5,加密实现在controller层
        public String EncodeByMd5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
            /**确定计算方法,使用base64 */
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            BASE64Encoder base64en = new BASE64Encoder();
            //加密字符串
            String newstr = base64en.encode(md5.digest(str.getBytes("UTF-8")));

            return newstr;
    }

    /**用户获取OTP短信的接口。入参手机号，出参异常处理
     * method参数是POST映射
     * consumes使用的是传统的HTTP的URLencoded方式
     * @CrossOrigin(allowCredentials = "true",allowedHeaders = "*") 跨域请求的正确写法，加在类上面
     * * */

    @RequestMapping(value = "/getotp",method = {RequestMethod.POST},consumes ={CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType getOtp(@RequestParam(name="telphone")String telphone){
        //需要按照一定规则生成OTP验证码。
        Random random = new Random();
        //此时随机数取值为0~5个9
        int  randomInt = random.nextInt(99999);
        //在随机数的基础上加上10000,随机数取值变成了10000~109999
        randomInt += 10000;
        //随机数转为字符串赋值给optCode
        String otpCode = String.valueOf(randomInt);


        /*将OTP验证码同用户手机号关联 k-v对形式，
         * k是手机号，v是验证码。企业级应用会上redis
         * 这里先用HttpSession来做
         * telpone和otpCode关联起来
         * 真实的前后呼应，和session有关的就只有这行了*/
//        httpServletRequest.getSession().setAttribute(telphone,otpCode);
        //将OTP验证码通过短信发送给用户，省略。先用打印调试
        System.out.println("telphone ="+ telphone + "&optCode ="+ otpCode);
        httpServletRequest.getSession().setAttribute(telphone,otpCode);
        String inSessionOtpCode = (String) this.httpServletRequest.getSession().getAttribute(telphone);
        System.out.println("再试试：#######"+inSessionOtpCode+"& session"+httpServletRequest.getSession()+"**&attribute"+httpServletRequest.getSession().getAttribute(telphone));
        /*很神奇，设置完直接就可以拿到值，但是再次获取又没了，推测是被覆盖了。这个方法存在某种特殊机制
         * 再试试：#######48097& sessionorg.apache.catalina.session.StandardSessionFacade@26ba79d9**&attribute48097
         */



        return CommonReturnType.create(null);
    }

/**接入统一使用commonReturnType，不用UserVo*/
    @RequestMapping("/get")
    @ResponseBody
    public CommonReturnType getUser(@RequestParam(name="id") Integer id) throws BusinessException {

        System.out.println("UserService:********"+userService);


        //调用用户service服务获取对应id的userModel用户对象并返回给前端
        UserModel userModel = userService.getUserById(id);

        //若获取的对应用户信息不存在,TomCat异常处理会返回500。我们需要拦截掉Tomcat的异常处理
        if (userModel == null){
           throw new BusinessException(EmBusinessError.USER_NOT_EXIST);
            //哪怕这里出空指针，页面也会定义为未知错误，而非404，500
//            userModel.setEncrptPassword("1233");
        }


        /**传入userModel，返回userVO给前端。
         * 即将核心领域用户模型转化为
         * UI使用的viewObject这类领域模型
         */
        //获得userVo对象
        UserVO userVO= convertFromModel(userModel);
        /**并将userVo通过CommonReturnType的create方法返回出去。
         * 即将userVO过一遍通用处理，返回通用对象*/
        return CommonReturnType.create(userVO);
    }
    /**这个方法实现 传入userModel，返回userVO*/
    private UserVO convertFromModel(UserModel userModel){
        //先判空拒绝空指针
        if (userModel == null){
            return null;
        }
        UserVO userVO =new UserVO();
        //使用Bean工具类实现copy，将UserModel copy到VO中。第2次copy
        BeanUtils.copyProperties(userModel,userVO);
        //最后返回VO
        return userVO;
    }


}
