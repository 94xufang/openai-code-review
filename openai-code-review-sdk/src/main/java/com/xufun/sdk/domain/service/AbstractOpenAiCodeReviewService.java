package com.xufun.sdk.domain.service;

import com.xufun.sdk.infrastructure.git.GitCommand;
import com.xufun.sdk.infrastructure.openai.IOpenAI;
import com.xufun.sdk.infrastructure.weixin.WeiXin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 编排一次完整流程：diff → 模型评审 → 持久化评审结果 → 微信通知。
 * 各步骤由子类实现具体基础设施调用。
 */
public abstract class AbstractOpenAiCodeReviewService implements IOpenAiCodeReviewService {
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
            logger.info("openai-code-review started");
            String diffCode = getDiffCode();
            String recommend = codeReview(diffCode);
            String logUrl = recordCodeReview(recommend);
            pushMessage(logUrl);
            logger.info("openai-code-review finished, logUrl={}", logUrl);
        } catch (Exception e) {
            logger.error("openai-code-review failed", e);
            throw new RuntimeException("openai-code-review failed", e);
        }
    }

    protected abstract String getDiffCode() throws IOException, InterruptedException;

    protected abstract String codeReview(String diffCode) throws Exception;

    protected abstract String recordCodeReview(String recommend) throws Exception;

    protected abstract void pushMessage(String logUrl) throws Exception;
}
