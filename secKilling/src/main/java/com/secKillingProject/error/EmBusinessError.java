package com.secKillingProject.error;

public enum EmBusinessError implements CommonError{
    //通用错误类型10001，参数不合法
    PARAMETER_VALIDATION_ERROR(10001,"参数不合法"),
    UNKNOWN_ERROR(10002,"未知错误"),


    //20000开头为用户信息相关错误定义。
    // 在分布式开发中通常需要有一个全局的统一状态码流转
    USER_NOT_EXIST(20001,"用户不存在"),
    USER_LOGIN_FAIL(20002,"用户手机号或者密码不正确"),
    USER_NOT_LOGIN(20003,"用户还未登录"),

    //    30000开头表示交易信息错误
    STOCK_NOT_ENOUGH(30000,"库存不足"),
    MQ_SEND_FAIL(30002,"库存异步消息失败"),
    RATELIMIT(30003,"活动太火爆，请稍后再试"),

    ;



    private EmBusinessError(int errCode,String errMsg){
        this.errCode=errCode;
        this.errMsg=errMsg;
    }
/**java的枚举可用于成员变量和属性,本质上是一个面向对象的类*/
    private int errCode;
    private String errMsg;





    @Override
    public int getErrCode() {
        return this.errCode;
    }

    @Override
    public String getErrMsg() {
        return this.errMsg;
    }

    /**通用错误类型描述的改动，
     * 不同场景下通用错误码的信息描述，
     * 即表示的意义是不同的*/

    @Override
    public CommonError setErrMsg(String errMsg) {
        this.errMsg =errMsg;
        return this;
    }
}
