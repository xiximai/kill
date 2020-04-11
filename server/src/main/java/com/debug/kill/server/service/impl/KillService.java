package com.debug.kill.server.service.impl;

import com.debug.kill.model.entity.ItemKill;
import com.debug.kill.model.entity.ItemKillSuccess;
import com.debug.kill.model.mapper.ItemKillMapper;
import com.debug.kill.model.mapper.ItemKillSuccessMapper;
import com.debug.kill.server.enums.SysConstant;
import com.debug.kill.server.service.IKillService;
import com.debug.kill.server.service.RabbitSenderService;
import com.debug.kill.server.utils.RandomUtil;
import com.debug.kill.server.utils.SnowFlake;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.jboss.netty.handler.codec.oneone.OneToOneDecoder;
import org.joda.time.DateTime;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class KillService implements IKillService {
   @Autowired
   private ItemKillSuccessMapper itemKillSuccessMapper;

   @Autowired
   private ItemKillMapper itemKillMapper;


   private SnowFlake snowFlake=new SnowFlake(2, 3);

   @Autowired
   private RabbitSenderService rabbitSenderService;




    @Override
    public Boolean killItem(Integer killId, Integer userId) throws Exception {
        // TODO: 2019/11/23 :判断当前用户是否已经抢购过这个商品
        boolean result=false;
        if(this.itemKillSuccessMapper.countByKillUserId(killId, userId)<=0){
            //可以抢购
            //获取商品信息
            // TODO: 2019/11/23 判断当前抢购列表的商品库存量
            ItemKill itemKill=this.itemKillMapper.selectById(killId);
            if(itemKill!=null&&1==itemKill.getCanKill()){
                int res = this.itemKillMapper.updateKillItem(killId);
                //数据库update会返回update中符合匹配的数据条数
                // TODO: 2019/11/23  商品库存减一
                if(res>0){
                    // TODO: 2019/11/23  判断是否库存是否扣减成功
                    this.commonRecordKillSuccessInfo(itemKill,userId);
                    result=true;
                }
                else {
                    throw new Exception("您已经抢购过该商品了!");
                }
            }
        }
        return result;
    }

    private void commonRecordKillSuccessInfo(ItemKill kill, Integer userId) {
        //TODO:记录抢购成功后生成的秒杀订单记录

        ItemKillSuccess entity=new ItemKillSuccess();
        String orderNo=String.valueOf(snowFlake.nextId());


        entity.setCode(orderNo); //雪花算法
        entity.setItemId(kill.getItemId());
        entity.setKillId(kill.getId());
        entity.setUserId(userId.toString());
        entity.setStatus(SysConstant.OrderStatus.SuccessNotPayed.getCode().byteValue());
        entity.setCreateTime(DateTime.now().toDate());
        //TODO:学以致用，举一反三 -> 仿照单例模式的双重检验锁写法
        if (itemKillSuccessMapper.countByKillUserId(kill.getId(),userId) <= 0){
            int res=itemKillSuccessMapper.insertSelective(entity);

            if (res>0){
                //TODO:进行异步邮件消息的通知=rabbitmq+mail
                rabbitSenderService.sendKillSuccessEmailMsg(orderNo);

                //TODO:入死信队列，用于 “失效” 超过指定的TTL时间时仍然未支付的订单
                rabbitSenderService.sendKillSuccessOrderExpireMsg(orderNo);
            }
        }
    }


    /**
     * 商品秒杀核心业务逻辑的处理-mysql的优化
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Boolean killItemV2(Integer killId, Integer userId) throws Exception {
        Boolean result=false;

        //TODO:判断当前用户是否已经抢购过当前商品
        if (itemKillSuccessMapper.countByKillUserId(killId,userId) <= 0){
            //TODO:A.查询待秒杀商品详情
            ItemKill itemKill=itemKillMapper.selectByIdV2(killId);

            //TODO:判断是否可以被秒杀canKill=1?
            if (itemKill!=null && 1==itemKill.getCanKill() && itemKill.getTotal()>0){
                //TODO:B.扣减库存-减一
                int res=itemKillMapper.updateKillItemV2(killId);

                //TODO:扣减是否成功?是-生成秒杀成功的订单，同时通知用户秒杀成功的消息
                if (res>0){
                    commonRecordKillSuccessInfo(itemKill,userId);

                    result=true;
                }
            }
        }else{
            throw new Exception("您已经抢购过该商品了!");
        }
        return result;
    }


    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    /**
     * redis 悲观锁
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Boolean killItemV3(Integer killId, Integer userId) throws Exception {
        Boolean result=false;

        //TODO:判断当前用户是否已经抢购过当前商品
        if (itemKillSuccessMapper.countByKillUserId(killId,userId) <= 0){
            // TODO: 2019/11/28 借助redis得原子操作实现redis分布式锁-对共享操作-资源进行控制
            ValueOperations valueOperations=stringRedisTemplate.opsForValue();
            final  String key=new StringBuffer().append(killId).append(userId).append("Redislock").toString();
            final String value= RandomUtil.generateOrderCode(); //产生编号
            boolean cacheRes=valueOperations.setIfAbsent(key, value);//表示当前的Key如果不存在于缓存中，那么将设置值成功，反之，如果Key已经存在于缓存中了，那么设置值将不成功！通过这一特性，我们可以将“KillId和UserId的一一对应关系~即一个人只能抢到一个商品”组合在一起作为Key！
            //redis服务器宕机
            if(cacheRes){
                stringRedisTemplate.expire(key,30, TimeUnit.SECONDS); //过时得线程销毁
                    try {
                        ItemKill itemKill=itemKillMapper.selectById(killId);
                        if(itemKill!=null&&itemKill.getCanKill()==1&&itemKill.getTotal()>0){
                            int res=itemKillMapper.updateKillItemV2(killId);
                            if(res>0){
                                commonRecordKillSuccessInfo(itemKill, userId);

                                result=true;
                            }
                        }

                    }catch (Exception e){
                            throw new Exception("没抢购得到");
                    }finally {
                        //释放锁
                            if(value.equals(valueOperations.get(key).toString())){
                                stringRedisTemplate.delete(key);

                            }
                    }
            }

        }else{
            throw new Exception("您已经抢购过该商品了!");
        }
        return result;
    }


    @Autowired
    private RedissonClient redissonClient;
    /**
     * redissond的分布式锁
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Boolean killItemV4(Integer killId, Integer userId) throws Exception {
        boolean result=false;

        final String lockkey=new StringBuffer().append(killId).append(userId).append("RedissonLock").toString();
        RLock lock=redissonClient.getLock(lockkey);

        try{
            boolean cacheRes=lock.tryLock(30, 10, TimeUnit.SECONDS);
            if(cacheRes){
                // TODO: 2019/11/28 核心业务逻辑的处理
                if (itemKillSuccessMapper.countByKillUserId(killId,userId)<=0){
                    ItemKill itemKill=itemKillMapper.selectByIdV2(killId);
                    if(itemKill!=null&&itemKill.getCanKill()==1&&itemKill.getTotal()>0){
                        int res=itemKillMapper.updateKillItemV2(killId);
                        if (res > 0) {
                            commonRecordKillSuccessInfo(itemKill,userId);
                            result=true;
                        }


                    }
                }
            }else {
                throw new Exception("出现错误");
            }
        }catch (Exception e){
            throw new Exception("出现错误");
        }finally {
            lock.unlock(); //主动释放锁
        }
        return result;
    }

    @Autowired
    private CuratorFramework curatorFramework;

    private final static String pathPrefix="/kill/zklock/";
    /**
     * zookeeper分布式锁
     * @param killId
     * @param userId
     * @return
     * @throws Exception
     */
    @Override
    public Boolean killItemV5(Integer killId, Integer userId) throws Exception {
        boolean result =false;
        InterProcessMutex mutex=new InterProcessMutex(curatorFramework, pathPrefix+killId+userId+"lock");

        try{
            if(mutex.acquire(10, TimeUnit.SECONDS)){
                if (itemKillSuccessMapper.countByKillUserId(killId,userId)<=0){
                    ItemKill itemKill=itemKillMapper.selectByIdV2(killId);
                    if(itemKill!=null&&itemKill.getCanKill()==1&&itemKill.getTotal()>0){
                        int res=itemKillMapper.updateKillItemV2(killId);
                        if (res > 0) {
                            commonRecordKillSuccessInfo(itemKill,userId);
                            result=true;
                        }


                    }
                }
            }else {
                throw new Exception("出现错误");
            }
        }catch (Exception e){
                throw new Exception("抢购失败");
        }finally {
            if(mutex!=null){
                mutex.release();
        }

        return result;
    }
}}
