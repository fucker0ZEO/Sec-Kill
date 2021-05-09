package com.secKillingProject.service.model;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**redis默认实现序列化的方式是JDK的实现。
 * 因此这里将UserModel存入session，
 * 就需要实现Serializable接口*/
public class UserModel implements Serializable {
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Byte getGender() {
        return gender;
    }

    public void setGender(Byte gender) {
        this.gender = gender;
    }



    public String getTelphone() {
        return telphone;
    }

    public void setTelphone(String telphone) {
        this.telphone = telphone;
    }

    public String getRegisterMode() {
        return registerMode;
    }

    public void setRegisterMode(String registerMode) {
        this.registerMode = registerMode;
    }

    public String getThirdPartyId() {
        return thirdPartyId;
    }

    public void setThirdPartyId(String thirdPartyId) {
        this.thirdPartyId = thirdPartyId;
    }

    public String getEncrptPassword() {
        return encrptPassword;
    }

    public void setEncrptPassword(String encrptPassword) {
        this.encrptPassword = encrptPassword;
    }
    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    private Integer id;
    @NotBlank(message = "用户名不能为空")
    private String  name;
    @NotNull(message = "性别不能为空")
    private Byte gender;
    @NotNull(message = "年龄不能为空")
    @Min(value = 0,message = "年龄必须大于0")
    @Max(value = 130,message = "年龄必须小于130岁")
    private Integer age;
    @NotNull(message = "手机号不能为空")
    private String telphone;
    private String registerMode;
    private String thirdPartyId;
/**encrptPassword是属于用户对象的，
 * 但是仅仅因为数据模型层的关系，把它设计到了2张表中
 * 但是对于Java的领域模型来说，他就是属于UserModel的
 * 因此model层才是真正出来业务逻辑的核心模块，
 * 而dataModel仅仅只是对于数据库的映射
 * 因此UserService的返回值是UserModel
 *     @NotBlank(message = "用户名不能为空")这个注解表示对应的字段为不能为空字符串或者是null，
 *     如果为null则返回message中的错误信息
 * */
    @NotNull(message = "密码不能为空")
    private String encrptPassword;

}
