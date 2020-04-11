package com.debug.kill.server.controller;

import jodd.util.StringUtil;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.DisabledAccountException;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.crypto.hash.Md5Hash;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户登录controller
 */
@Controller
public class UserController {
    private final  static Logger log= LoggerFactory.getLogger(UserController.class);

    @Autowired
    private Environment env;

    /**
     * 跳转到登录页面
     * @return
     */
    @RequestMapping(value={"/to/login","/unauth"})
    public String tolOGIN(){
        return "login";
    }

    @RequestMapping(value="/login",method = RequestMethod.POST)
    public String Login(@RequestParam String userName, @RequestParam String password, ModelMap map){
        String errorMsg="";
        try{
            if(!SecurityUtils.getSubject().isAuthenticated()){
                //加密
                String newPsd=new Md5Hash(password,env.getProperty("shiro.encrypt.password.salt")).toString();
                UsernamePasswordToken token = new UsernamePasswordToken(userName, newPsd);
                SecurityUtils.getSubject().login(token);
            }
        }catch (UnknownAccountException e){
            errorMsg=e.getMessage();
            map.addAttribute("userNmae", userName);
        }catch (DisabledAccountException e){
            errorMsg=e.getMessage();
            map.addAttribute("userNmae", userName);

        }catch (IncorrectCredentialsException e){
            errorMsg=e.getMessage();
            map.addAttribute("userName", userName);
        }
        catch (Exception e){
            errorMsg=e.getMessage();
            e.printStackTrace();
        }
        finally {
            System.out.println("确认情况");
        }

        if (StringUtil.isBlank(errorMsg)) {
            return "redirect:/index";
        } else {
            map.addAttribute("errorMsg", errorMsg);
            return "login";
        }
    }

    @RequestMapping(value = "/logout")
    public String Logout(){
        SecurityUtils.getSubject().logout();
        return "login";
    }
}
