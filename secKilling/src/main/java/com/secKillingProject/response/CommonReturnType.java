package com.secKillingProject.response;

public class CommonReturnType {
    /**表明对应请求的返回处理结果：”success“或”fail"
     * success意为成功，fail意失败
     * 前端认识错误是根据http返回的status值，例如为500
     * 而照理来说只要服务器能够受理请求，status都应该是200
     * 如果业务逻辑中有任何错误，应该通过业务逻辑的错误标识返回，
     * 而非http status。http status 不足以准确的描述业务错误
     * 如果服务器的处理是正常的，这边返回success，
     * 如果异常则返回fail，并且status是success的情况
     * */
    private String status;
    private Object data;

    /**若status=success，则data内返回前端所需要的json数据
     *若status=fail，则data内使用通用的错误码格式
     *
     * 定义一个通用的创建方法
     *
     * 目的：当controller完成了处理，调用对应的create方法，如果不带任何status,
     * 那对应的status就是success，然后创建了对应的CommonReturnType，并把对应的值返回
     *
     * */
    public static CommonReturnType create(Object result){
        return CommonReturnType.create(result,"success");

    }
    /**使用函数的重载方式做了一个构造方法*/
    public static CommonReturnType create(Object result,String status){
        CommonReturnType type = new CommonReturnType();
        type.setStatus(status);
        type.setData(result);
        return  type;
    }



    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
