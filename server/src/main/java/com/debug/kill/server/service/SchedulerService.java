package com.debug.kill.server.service;

import com.debug.kill.model.entity.ItemKillSuccess;
import com.debug.kill.model.mapper.ItemKillSuccessMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 定时检查
 */
@Service
public class SchedulerService {
    private static  final Logger log= LoggerFactory.getLogger(SchedulerService.class);

    @Autowired
    private ItemKillSuccessMapper itemKillSuccessMapper;

    @Autowired
    private Environment env;

    /***
     * 定时获取支付状态status的订单并判断是否超过TTL，然后进行失效
     *
     */
    @Scheduled(cron = "0 0/59 * * * ?  ")
    public void schedulerExpireOrders(){
        try {
            List<ItemKillSuccess> list=itemKillSuccessMapper.selectExpireOrders();
            log.info("进入计时器", list);
            if(list!=null&&!list.isEmpty()){
                list.stream().forEach(i->{
                    if(i!=null&&i.getDiffTime() > env.getProperty("scheduler.expire.orders.time",Integer.class)){
                        itemKillSuccessMapper.expireOrder(i.getCode());

                    }
                });
            }
            }catch(Exception e){
            log.error("定时获取status=0得订单并判断是否超过ttl：",e.fillInStackTrace());

        }
    }
}
