package com.debug.kill.server.service;

import com.debug.kill.model.mapper.UserMapper;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.PrincipalCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.debug.kill.model.entity.User;


import java.util.Objects;

/**
 * 用户自定义得realm-用于shiro得认证授权
 */
public class CustomRealm extends AuthorizingRealm {

    private final static Logger log= LoggerFactory.getLogger(CustomRealm.class);

    @Autowired
    private UserMapper userMapper;

    private final static Long sessinKeyTimeOut=3600_000L;



    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken authenticationToken) throws AuthenticationException {
        UsernamePasswordToken token= (UsernamePasswordToken) authenticationToken;
        String userName=token.getUsername();
        String passWord=String.valueOf(token.getPassword());
        log.info("当前登录得用户名={} 密码={}", userName,passWord);

        User user=userMapper.selectByUserName(userName);

        if(user==null){
            throw new UnknownAccountException("用户不存在");
        }
        if (!Objects.equals(1,user.getIsActive().intValue())){
            throw new DisabledAccountException("当前用户已经被禁用");
        }
        if(!user.getPassword().equals(passWord)){
            throw new IncorrectCredentialsException("用户密码不正确");
        }
        SimpleAuthenticationInfo info=new SimpleAuthenticationInfo(user.getUserName(),passWord,getName());
        setSession("uid",user.getId());
        return info;
    }

    /**
     * 讲key与对象得value塞入shiro得session中-最终交给httpsession进行管理（如果是分布式session配置，那么就是交给radis管理）
     * @param key
     * @param value
     */
    private void setSession(String key, Integer value) {
        Session session= SecurityUtils.getSubject().getSession();
        if(session!=null){
            session.setAttribute(key,value);
            session.setTimeout(sessinKeyTimeOut);
        }
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principalCollection) {
        return null;
    }
}
