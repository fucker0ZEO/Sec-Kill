package com.secKillingProject.controller;

public class BaseController {
    /**声明变量
     * application/x-www-form-urlencoded
     * form 拼错
    */
    public static final String CONTENT_TYPE_FORMED ="application/x-www-form-urlencoded";

    /**定义exceptionHandler解决未被Controller层吸收的exception异常。
     * 对于web系统来说，controller层异常是业务处理的最后一道关口*/
//    @ExceptionHandler(Exception.class)
//    @ResponseStatus(HttpStatus.OK)
//    @ResponseBody
//    public Object handlerException(HttpServletRequest request, Exception ex){
//        Map<String, Object> responseData = new HashMap<>();
//
//        //如果返回的不是BusinessException，则为未知错误
//        if(ex instanceof BusinessException){
//
//            //强转为BusinessException对象
//            BusinessException businessException =(BusinessException)ex;
//            //这个object是寻找本地路径下的页面，如果找不到就返回404
//            //设置Data为responseData，最后把整个commonReturnType返回出去
//            responseData.put("errCode",businessException.getErrCode());
//            responseData.put("errMsg",businessException.getErrMsg());
//
//        }else {
//            //未知错误及描述
//            responseData.put("errCode", EmBusinessError.UNKNOWN_ERROR.getErrCode());
//            responseData.put("errMsg",EmBusinessError.UNKNOWN_ERROR.getErrMsg());
//        }
//        //静态类美化
//        return CommonReturnType.create(responseData,"fail");
//
//    }

}
