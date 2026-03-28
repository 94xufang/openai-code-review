package com.xufun.sdk;

import com.xufun.sdk.domain.service.impl.OpenAiCodeReviewService;
import com.xufun.sdk.infrastructure.git.GitCommand;
import com.xufun.sdk.infrastructure.openai.IOpenAI;
import com.xufun.sdk.infrastructure.openai.impl.ChatGLM;
import com.xufun.sdk.infrastructure.weixin.WeiXin;

/**
 * 入口：从环境变量读取 GitHub / 智谱 / 微信配置，串联 diff → AI 评审 → 日志仓库推送 → 模板消息通知。
 * 在 CI（如 GitHub Actions）中由对应 Secrets 注入上述变量。
 */
public class OpenAiCodeReview {

    public static void main(String[] args) {
        GitCommand gitCommand = new GitCommand(
                getEnv("GITHUB_REVIEW_LOG_URI"),
                getEnv("GITHUB_TOKEN"),
                getEnv("COMMIT_PROJECT"),
                getEnv("COMMIT_BRANCH"),
                getEnv("COMMIT_AUTHOR"),
                getEnv("COMMIT_MESSAGE")
        );

        /* 微信模板占位：项目 / 分支 / 作者 / 说明 对应模板中的 repo_name、branch_name 等 */
        WeiXin weiXin = new WeiXin(
                getEnv("WEIXIN_APPID"),
                getEnv("WEIXIN_SECRET"),
                getEnv("WEIXIN_TOUSER"),
                getEnv("WEIXIN_TEMPLATE_ID")
        );

        IOpenAI openAI = new ChatGLM(getEnv("CHATGLM_APIHOST"), getEnv("CHATGLM_APIKEYSECRET"));

        OpenAiCodeReviewService openAiCodeReviewService = new OpenAiCodeReviewService(gitCommand, openAI, weiXin);
        openAiCodeReviewService.exec();
    }

    private static String getEnv(String key) {
        String value = System.getenv(key);
        if (null == value || value.isEmpty()) {
            throw new RuntimeException("Environment variable " + key + " is not set or empty. Please check your GitHub secrets configuration.");
        }
        return value;
    }

}
