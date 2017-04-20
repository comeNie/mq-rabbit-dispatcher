/*
 *
 * (C) Copyright 2017 Ymatou (http://www.ymatou.com/). All rights reserved.
 *
 */

package com.ymatou.mq.rabbit.dispatcher.facade.impl;



import com.alibaba.dubbo.config.annotation.Service;
import com.alibaba.fastjson.JSON;
import com.ymatou.mq.infrastructure.model.Message;
import com.ymatou.mq.infrastructure.util.NetUtil;
import com.ymatou.mq.rabbit.dispatcher.facade.MessageDispatchFacade;
import com.ymatou.mq.rabbit.dispatcher.facade.model.DispatchMessageReq;
import com.ymatou.mq.rabbit.dispatcher.facade.model.DispatchMessageResp;
import com.ymatou.mq.rabbit.dispatcher.service.MessageDispatchService;
import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;


/**
 * @author luoshiqian 2016/8/31 14:13
 */
@Service(protocol = "dubbo")
@Component
public class MessageDispatchFacadeImpl implements MessageDispatchFacade {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Autowired
    private MessageDispatchService messageDispatchService;

    @Override
    public DispatchMessageResp dispatch(DispatchMessageReq req){
        //构造请求消息
        Message msg = this.buildMessage(req);

        //接收发布消息
        messageDispatchService.dispatch(msg);

        //FIXME:总是返回true??? 上一步可能返回false
        //返回
        DispatchMessageResp resp = new DispatchMessageResp();
        resp.setUuid(msg.getId());
        resp.setSuccess(true);
        return resp;
    }

    /**
     * 构造请求消息
     * @param req
     * @return
     */
    Message buildMessage(DispatchMessageReq req){
        Message msg = new Message();
        msg.setAppId(req.getApp());
        msg.setQueueCode(req.getCode());

        //FIXME:外部应该不能指定内部真正使用的ID!!
        if(StringUtils.isBlank(req.getId())){
            msg.setId(ObjectId.get().toString());
        }else{
            msg.setId(req.getId());
        }
        msg.setBizId(req.getMsgUniqueId());
        msg.setBody(JSON.toJSONStringWithDateFormat(req.getBody(), DATE_FORMAT));
        msg.setClientIp(req.getIp());
        msg.setRecvIp(NetUtil.getHostIp());
        msg.setCreateTime(new Date());
        return msg;
    }
}
