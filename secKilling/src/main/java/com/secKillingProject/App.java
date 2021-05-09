package com.secKillingProject;

import com.secKillingProject.dao.UserDOMapper;
import com.secKillingProject.dataObject.UserDO;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hello world!
 *
 */

/**
 *  springBootApplication作用相似，不同的是这个会指定为主启动类
 *  scanBasePackages = {"com.secKillingProject"} 的作用是从这个包下逐级扫描装配
 *  **/
@SpringBootApplication(scanBasePackages = {"com.secKillingProject"})
@RestController
@MapperScan("com.secKillingProject.dao")
public class App {
    public static void main(String[] args) {
        System.out.println("Hello World!");
        SpringApplication.run(App.class,args);
    }

    /**使用@Autowired注入 userDOMapper对象*/
    @Autowired
    private UserDOMapper userDOMapper;


/**使用@RequestMapping，
 * 当程序访问到控制层时，
 * 用户存在返回用户id，不存在则返回用户不存在*/
    @RequestMapping
    public String home() {
        /**查询主键测试 */
        UserDO userDO = userDOMapper.selectByPrimaryKey(1);
        if (userDO == null) {
            return "用户不存在";
        } else {
            return userDO.getName();
        }
    }
}
