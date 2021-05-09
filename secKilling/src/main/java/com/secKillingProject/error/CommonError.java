package com.secKillingProject.error;

/**
 * @author fucker
 */
public interface CommonError {
    public int getErrCode();
    public String getErrMsg();
    public CommonError setErrMsg(String errMsg);
}
