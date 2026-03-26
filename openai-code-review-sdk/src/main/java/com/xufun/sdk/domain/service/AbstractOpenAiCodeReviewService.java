package com.xufun.sdk.domain.service;

import com.xufun.sdk.infrastructure.git.GitCommand;
import com.xufun.sdk.infrastructure.openai.IOpenAI;
import com.xufun.sdk.infrastructure.weixin.WeiXin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class AbstractOpenAiCodeReviewService implements IOpenAiCodeReviewService{
    private final Logger logger = LoggerFactory.getLogger(AbstractOpenAiCodeReviewService.class);

    protected final GitCommand gitCommand;
    protected final IOpenAI openAI;
    protected final WeiXin weiXin;

    public AbstractOpenAiCodeReviewService(GitCommand gitCommand, IOpenAI openAI, WeiXin weiXin) {
        this.gitCommand = gitCommand;
        this.openAI = openAI;
        this.weiXin = weiXin;
    }

    @Override
    public void exec() {
        try {
            logger.info("openai-code-review start...");
            
            // 1. 获取提交代码
            logger.info("getting diff code...");
            String diffCode = getDiffCode();
            logger.info("got diff code, length: {}", diffCode.length());
            
            // 2. 开始评审代码
            logger.info("start code review...");
            String recommend = codeReview(diffCode);
            logger.info("code review completed");
            
            // 3. 记录评审结果；返回日志地址
            logger.info("recording code review result...");
            String logUrl = recordCodeReview(recommend);
            logger.info("code review result recorded, logUrl: {}", logUrl);
            
            // 4. 发送消息通知；日志地址、通知的内容
            logger.info("sending wechat message...");
            pushMessage(logUrl);
            
            logger.info("openai-code-review completed!");
        } catch (Exception e) {
            logger.error("openai-code-review error", e);
            throw new RuntimeException("openai-code-review failed", e);
        }
    }

    protected abstract String getDiffCode() throws IOException, InterruptedException;

    protected abstract String codeReview(String diffCode) throws Exception;

    protected abstract String recordCodeReview(String recommend) throws Exception;

    protected abstract void pushMessage(String logUrl) throws Exception;
}
