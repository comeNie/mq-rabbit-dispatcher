package com.ymatou.mq.rabbit.dispatcher.service;

import java.util.Optional;
import java.util.function.Function;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import com.ymatou.mq.infrastructure.model.CallbackConfig;
import com.ymatou.mq.infrastructure.model.CallbackMessage;
import com.ymatou.mq.infrastructure.model.QueueConfig;
import com.ymatou.mq.infrastructure.service.MessageConfigService;
import com.ymatou.mq.infrastructure.service.MessageService;
import com.ymatou.mq.rabbit.dispatcher.config.FileDbConf;
import org.apache.commons.lang.SerializationUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ymatou.mq.infrastructure.filedb.FileDb;
import com.ymatou.mq.infrastructure.filedb.FileDbConfig;
import com.ymatou.mq.infrastructure.filedb.PutExceptionHandler;
import com.ymatou.mq.infrastructure.model.Message;

/**
 * 本地消息文件列表处理service Created by zhangzhihua on 2017/3/24.
 */
@Component
public class FileQueueProcessorService implements Function<Pair<String, String>, Boolean>, PutExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(FileQueueProcessorService.class);

    private FileDb fileDb;

    @Autowired
    private FileDbConf fileDbConf;

    @Autowired
    private DispatchCallbackService dispatchCallbackService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageConfigService messageConfigService;

    public FileDb getFileDb() {
        return fileDb;
    }

    public void setFileDb(FileDb fileDb) {
        this.fileDb = fileDb;
    }

    @PostConstruct
    public void init() {
        FileDbConfig fileDbConfig = FileDbConfig.newInstance()
                .setDbName(this.fileDbConf.getDbName())
                .setDbPath(this.fileDbConf.getDbPath())
                .setConsumerThreadNums(this.fileDbConf.getConsumerThreadNums())
                .setConsumeDuration(this.fileDbConf.getConsumeDuration())
                .setMaxConsumeSizeInDuration(this.fileDbConf.getMaxConsumeSizeInDuration())
                .setConsumer(this)
                .setPutExceptionHandler(this);

        fileDb = FileDb.newFileDb(fileDbConfig);
    }


    /**
     * 保存成文件队列
     * 
     * @param message
     */
    public boolean saveMessageToFileDb(Message message) {
        try {
            return fileDb.syncPut(message.getId(), Message.toJsonString(message));
        } catch (Exception e) {
            logger.error("sync put file db error.",e);
            return false;
        }
    }

    /**
     * 消费从文件获取到的数据 入库成功 返回true
     * 
     * @param pair
     * @return
     */
    @Override
    public Boolean apply(Pair<String, String> pair) {
        Boolean success = Boolean.FALSE;
        try {
            //消费数据
            Message message = Message.fromJson(pair.getValue());
            //插消息
            messageService.saveMessage(message);
            //进行回调
            QueueConfig queueConfig = messageConfigService.getQueueConfig(message.getAppId(),message.getQueueCode());
            for(CallbackConfig callbackConfig:queueConfig.getCallbackCfgList()){
                dispatchCallbackService.invoke(convertMessage(message,callbackConfig.getCallbackKey()));
            }
        } catch (Exception e) {
            logger.error("save message to mongo error", e);
            //TODO 出错时处理?
            return true;
        }
        return true;
    }

    /**
     * 转化为CallbackMessage
     * @param message
     * @return
     */
    CallbackMessage convertMessage(Message message,String callbackKey){
        CallbackMessage callbackMessage = new CallbackMessage();
        callbackMessage.setAppId(message.getAppId());
        callbackMessage.setQueueCode(message.getQueueCode());
        callbackMessage.setCallbackKey(callbackKey);
        callbackMessage.setId(message.getId());
        callbackMessage.setBizId(message.getBizId());
        callbackMessage.setBody(message.getBody());
        return callbackMessage;
    }

    @Override
    public void handleException(String key, String value, Optional<Throwable> throwable) {
        //同步保存 不用处理
    }

    @PreDestroy
    public void destroy(){
        fileDb.close();
    }
}
