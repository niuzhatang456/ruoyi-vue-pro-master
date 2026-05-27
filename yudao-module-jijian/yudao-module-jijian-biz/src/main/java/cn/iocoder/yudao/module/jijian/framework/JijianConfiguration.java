package cn.iocoder.yudao.module.jijian.framework;

import cn.iocoder.yudao.framework.swagger.config.YudaoSwaggerAutoConfiguration;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * jijian 模块的 Web 组件配置
 *
 * 注册 Swagger API 分组，使纪检模块接口在 Knife4j 文档中独立展示。
 * 参考：cn.iocoder.yudao.module.system.framework.web.config.SystemWebConfiguration
 */
@Configuration(proxyBeanMethods = false)
public class JijianConfiguration {

    @Bean
    public GroupedOpenApi jijianGroupedOpenApi() {
        return YudaoSwaggerAutoConfiguration.buildGroupedOpenApi("jijian");
    }

}
