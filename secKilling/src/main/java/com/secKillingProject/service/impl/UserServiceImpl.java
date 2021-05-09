package com.secKillingProject.service.impl;

import com.secKillingProject.dao.UserDOMapper;
import com.secKillingProject.dao.UserPasswordDOMapper;
import com.secKillingProject.dataObject.UserDO;
import com.secKillingProject.dataObject.UserPasswordDO;
import com.secKillingProject.error.BusinessException;
import com.secKillingProject.error.EmBusinessError;
import com.secKillingProject.service.UserService;
import com.secKillingProject.service.model.UserModel;
import com.secKillingProject.validator.ValidationResult;
import com.secKillingProject.validator.ValidatorImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

/**指定为service对象*/
@Service
public class UserServiceImpl implements UserService {

    /**注入userDOMapper*/
    @Autowired
    private UserDOMapper userDOMapper;

    /**注入UserPassWordMapper*/
    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;

    @Autowired
    private ValidatorImpl validator;

    /**注入redis,后面用来将userModel缓存到redis中
     * 可以减少查DB
     * */
    @Autowired
    private RedisTemplate redisTemplate;

   @Override
    public UserModel getUserById(Integer id) {
        //调用userDOMapper,根据主键ID,获取到对应用户的dataobject对象
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);
        //通过UserService获取到用户的领域对象——UserModel

        if (userDO == null){
            return null;
        }

        //调试得userID有值，而selectByUserId无值，因此空指针异常
        if (userDO.getId() != null){
            System.out.println("UserID:********"+userDO.getId());

            System.out.println("Userpassword:********"+userPasswordDOMapper);
        }


        //通过用户ID获取对应的用户加密密码信息
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        //可以看到，这里获取到的只是一个对象 com.secKillingProject.dataObject.UserPasswordDO@30e636e
        System.out.println("password表的元数据******："+userPasswordDO);
        /*通过这个将userModel转出来并且返回给controller层*/
        return convertFromDataObject(userDO,userPasswordDO);
    }

    /**实现register方法，校验判空.进行注册
     *  @Transactional注解为 事务
     * */
    @Override
    @Transactional
    public void register(UserModel userModel) throws BusinessException {
        if(userModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }

        ValidationResult result = validator.validate(userModel);
        if (result.isHasErrors()){

            /*获取getErrMsg中的Map,Map中有对应的错误信息*/
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,result.getErrMsg());
        }
        /*这里为啥要用insertSelective,而非insert？
          insertSelective会多一个先判断为null的操作，为null就不执行操作，不为null才执行。
          用insert会覆盖掉数据库中的默认值，相当于删库。
          数据库设计的过程中尽量避免使用null字段。Java对于null的空指针是非常脆弱的
          但是有时候也是有null的，例如第3方登录时，手机号就是null....如果用负数来替代就会遇见唯一索引相同的情况
          */


        /*真正的返回方式，这里用了insertSelective返回*/
        UserDO userDO = convertFromModel(userModel);
        try {
            userDOMapper.insertSelective(userDO);
        }catch (DuplicateKeyException exception){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"手机号已重复注册");
        }
        userModel.setId(userDO.getId());
        System.out.println("这里真的能拿到到id???"+userDO.getId());
        /*查一下userModel,逻辑大概清楚了。userModel中的user_id是落库后自增的结果，落库之前并没有id的值.
        其他值都可以通过前端传入，但是这个ID是主键自增
         上面看上去获取了id。但是id还是null。
         即这里拿不到userModel，后面的userPasswordDO也为null
         后面的passWord表写入的值为null，导致注册成功，但是密码表中无数据，后面报错
         解决方案在对应的SQL的XML文件中加入 keyProperty="id" useGeneratedKeys="true"
         这是Mybatis提供的机制，未落库依旧可以拿到数据，用来解决不用外键写入两张表存在的问题
         */
        UserPasswordDO userPasswordDO = convertPasswordFromModel(userModel);

        /**调用insertSelective方法，把userPasswordDO写回数据库*/
        userPasswordDOMapper.insertSelective(userPasswordDO);
        return;
    }

    @Override
    /*根据手机号和密码做登录验证*/
    public UserModel validateLogin(String telphone, String encrptPassword) throws BusinessException {
        //通过用户的手机号查询用户信息,整行记录变成了userDO对象
        UserDO userDO = userDOMapper.selectByTelphone(telphone);
        if (userDO == null){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        //通过userDO这行记录对象获取到记录中的id，然后将id赋值给密码表的uid，根据uid查密码
        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        //记录对象DO和这行记录对应的密码对象组装成model
        UserModel userModel = convertFromDataObject(userDO,userPasswordDO);

        /**比对信息内加密的密码是否和传输进来的密码相匹配.
         * encrptPassword是传进来的密码，getEncrptPassword是获取到的密码
         * userService对象在controller层就装了手机号和加密的密码
         * 手机号前面已经用到了，这里用到的就是密码*/
        if (!StringUtils.equals(encrptPassword,userModel.getEncrptPassword())){
            throw new BusinessException(EmBusinessError.USER_LOGIN_FAIL);
        }
        //用户登录成功，返回userModer给controller层
        return userModel;
    }

    /**实现userModel的缓存模型
     * 即将userModel缓存到redis中
     * 先根据id查询redis
     * redis中没有，再查DB，并将DB中的数据更新到Redis中
     * validate代表验证
     * */
    @Override
    public UserModel getUserByIdInCache(Integer id) {
        /*根据id到redis中查询userModel*/
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get("user_validate_" +id);

        /*如果redis中没有数据，即userModel为null，将userModel加到Redis中*/
        if (userModel == null){
            /*根据id，使用本类中getUserById的从DB中查userModel*/
            userModel = this.getUserById(id);
            /**拿到的userModel写回/更新到redis中
             * key是"user_validate_" + id，value是userModel
             * 过期时间设置为10分钟
             * */
            redisTemplate.opsForValue().set("user_validate_" +id,userModel);
            redisTemplate.expire("user_validate_" +id,10, TimeUnit.MINUTES);
            }
        //返回userModel,这里的是userModel缓存模型，即redis中的userModel
        return userModel;
    }

    /**实现密码。把UserModel转化成userPasswordDO*/
    private UserPasswordDO convertPasswordFromModel(UserModel userModel){
        if (userModel == null){
            return null;
        }
        UserPasswordDO userPasswordDO =new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptPassword());
//        这里获取的用户id
        userPasswordDO.setUserId(userModel.getId());
        System.out.println("是否真的能够拿到用户的ID：******"+userModel.getId());
        return userPasswordDO;
    }

    //实现model返回DataObject的方法
    private UserDO convertFromModel(UserModel userModel){
        if (userModel == null){
            return null;
        }
        UserDO userDO =new UserDO();
        BeanUtils.copyProperties(userModel,userDO);
        return userDO;
    }

    /**通过UserDO和UserPasswordDO组装成UserModel的对象
     * */
    private UserModel convertFromDataObject(UserDO userDO, UserPasswordDO userPasswordDO){
        if (userDO ==null){
            return null;
        }
        UserModel userModel = new UserModel();
        //第一次copy,userDO放入userModel
        BeanUtils.copyProperties(userDO,userModel);

        if (userPasswordDO !=null){
            //userPassword中的密码传入userModel。此后userModel中就有了EncrptPassword这个属性
            userModel.setEncrptPassword(userPasswordDO.getEncrptPassword());
//            加密后的密码：****asdfasdf
            System.out.println("加密后的密码：****"+userPasswordDO.getEncrptPassword());
        }
        return userModel;
    }
}
