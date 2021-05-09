package com.secKillingProject.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**当Spring容器内没有TomcatEmbeddedServletContainerFactory(Tomcat内嵌Servlet容器工厂)
 * 这个Bean时，会把此bean加载进spring容器中
 * WebServer Factory Customizer 见名知意，web server的工厂定制器
 * Configurable WebServer Factory 同样见名知意，可配置化的定制工厂
 * */
@Component
public class WebServiceConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    @Override
    public void customize(ConfigurableWebServerFactory configurableWebServerFactory) {

        //使用对应工厂类提供的接口定制化Tomcat connect。spring会把configurableWebServerFactory这个可配置化的工厂传入
        ((TomcatServletWebServerFactory)configurableWebServerFactory).addConnectorCustomizers(
                new TomcatConnectorCustomizer() {
                    @Override
                    public void customize(Connector connector) {
                     /* 重要参数ProtocolHandler，
                      * 强转为Http的Nio的protocol。protocol代表协议
                      * 这里的意思是获取协议头，Http的协议头。
                      * 后面通过这个协议头对象，set进KeepAlive的2个协议头的参数*/
                      Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
                      //定制化keepAliveTimeOut,设置30秒额你没有返回请求则自动端口keepAlive连接
                        protocol.setKeepAliveTimeout(30000);
                        //定制化maxKeepAliveRequest当客户端发送超过10000个请求则自动断开keepAlive连接
                        protocol.setMaxKeepAliveRequests(1000);
                    }
                }
        );
    }
}
