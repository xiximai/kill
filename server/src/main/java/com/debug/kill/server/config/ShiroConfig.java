package com.debug.kill.server.config;

import com.debug.kill.server.service.CustomRealm;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * shiro得通用化配置
 */
@Configuration
public class ShiroConfig {
    @Bean
    public CustomRealm customRealm(){
        return new CustomRealm();
    }

    @Bean
    public DefaultWebSecurityManager securityManager(){
        DefaultWebSecurityManager securityManager=new DefaultWebSecurityManager();
        securityManager.setRealm(customRealm());
        return securityManager;
    }
    @Bean
    public ShiroFilterFactoryBean shiroFilterFactoryBean(){
        ShiroFilterFactoryBean shiroFilterFactoryBean=new ShiroFilterFactoryBean();
        shiroFilterFactoryBean.setSecurityManager(securityManager());
        shiroFilterFactoryBean.setLoginUrl("/to/login");
        shiroFilterFactoryBean.setUnauthorizedUrl("/unauth");

        Map<String,String> filterChainDefinitionMap=new HashMap<>();
        filterChainDefinitionMap.put("/to/login", "anon");
        filterChainDefinitionMap.put("/**", "anon");
        filterChainDefinitionMap.put("/kill/execute", "authc");
        filterChainDefinitionMap.put("/item/detail/**", "authc");
        shiroFilterFactoryBean.setFilterChainDefinitionMap(filterChainDefinitionMap);
        return shiroFilterFactoryBean;
    }
}
