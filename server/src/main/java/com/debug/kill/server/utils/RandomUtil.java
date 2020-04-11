package com.debug.kill.server.utils;

import org.joda.time.DateTime;

import java.text.SimpleDateFormat;


import java.util.concurrent.ThreadLocalRandom;

/*
随机生成订单编号
 */
public class RandomUtil {
    private final static SimpleDateFormat format=new SimpleDateFormat("yyyyMMddHHmmssSS");
    private final static ThreadLocalRandom random=ThreadLocalRandom.current();

    // TODO: 2019/11/23  生成编号方式一
    public static String generateOrderCode(){
        // TODO: 2019/11/23 时间戳+随便编号
        return format.format(DateTime.now().toDate())+generateNumber(4);
    }

    // TODO: 2019/11/23 生成随机数 
    private static String generateNumber(final int num) {
        // TODO: 2019/11/23 选用线程安全的
        StringBuffer sb=new StringBuffer();
        for(int i=1;i<=num;i++){
            sb.append(random.nextInt(9));
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println(generateOrderCode());
    }
}
