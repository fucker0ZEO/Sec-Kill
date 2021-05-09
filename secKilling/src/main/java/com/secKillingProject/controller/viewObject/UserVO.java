package com.secKillingProject.controller.viewObject;

/**
 * @author fucker
 * MVVM 结构 modelCotroller就是model层
 * ViewController就是viewObjetc
 * view，data,model 3层的领域模型相似，但是每层的属性各有不同。
 * model层的领域模型就包含着这个用户的所有信息，data层则是跟数据库表对应的领域模型，只需和表一致即可
 * view层是和前端一致，只需提供展示给前端的属性数据。
 * 而密码，用户行为，登录方式这些都不是前端该知道的
 */
public class UserVO {
    private Integer id;

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
    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    private String  name;
    private Byte gender;
    private Integer age;
    private String telphone;
}
