package com.secKillingProject.service;

import com.secKillingProject.error.BusinessException;
import com.secKillingProject.service.model.UserModel;

public interface UserService {
    /**通过用户ID获取用户对象的方法*/
    UserModel getUserById(Integer id);
    /**处理用户的注册请求*/
    void register(UserModel userModel) throws BusinessException;
    /**
     * 登陆验证
     * @param telphone 用户注册的手机
     * @param encrptPassword 用户加密后的密码
     */
    UserModel validateLogin(String telphone,String encrptPassword) throws BusinessException;

    /**（获取）userModel的缓存模型
     * 后面查找userModel都从redis中取，redis找不到再从DB中找
     * 做一层缓存替代每次SQL查DB，SQL查DB太吃IO,磁盘速度慢....
     * */
    UserModel getUserByIdInCache(Integer id);
}
