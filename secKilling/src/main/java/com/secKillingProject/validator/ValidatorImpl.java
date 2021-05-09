package com.secKillingProject.validator;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Set;

/**
 * @author fucker
 * @Component 把它实现为springBean
 */
@Component
public class ValidatorImpl implements InitializingBean {

    private Validator validator;
    /**实现校验方法并返回校验结果*/
    public ValidationResult validate(Object bean){
        ValidationResult result =new ValidationResult();
        Set<ConstraintViolation<Object>> constraintViolationSet = validator.validate(bean);
        if (constraintViolationSet.size()>0){
            //有错误
            result.setHasErrors(true);
            constraintViolationSet.forEach(constraintViolation->{
                //errMsg是错误信息
                String errMsg = constraintViolation.getMessage();
                //知道是哪个字段错了
                String propertyName = constraintViolation.getPropertyPath().toString();
                result.getErrorMsgMap().put(propertyName,errMsg);

            });
        }
        return result;

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //将hibernate validator通过工厂初始化方式使其实例化。或者说这里写了一个校验器
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
    }
}
