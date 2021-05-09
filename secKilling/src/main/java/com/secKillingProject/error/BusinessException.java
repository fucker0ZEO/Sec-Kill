package com.secKillingProject.error;

/**任何导致程序跑不下去的异常都转到这类来，
 * 最后它会别springBoot的Controller层所捕获
 *
 * 这属于设计模式中的包装模式
 * 包装器业务异常类实现
 * @author fucker*/
public class BusinessException extends Exception implements CommonError{

    /**内部强关联一个CommonError,
     * 并且有相应的构造函数直接接受EmBusinessError的传参用于构造业务异常*/
    private CommonError commonError;
    public BusinessException(CommonError commonError){
        super();
        this.commonError =commonError;
    }
    /**接受自定义errMsg的方构造业务异常*/
    public BusinessException(CommonError commonError,String errMsg){
        super();
        this.commonError = commonError;
        this.commonError.setErrMsg(errMsg);
    }

    @Override
    public int getErrCode() {
        return this.commonError.getErrCode();
    }

    @Override
    public String getErrMsg() {
        return this.commonError.getErrMsg();
    }

    @Override
    public CommonError setErrMsg(String errMsg) {
        this.commonError.setErrMsg(errMsg);
        return this;
    }
}
