package com.xufun.sdk.infrastructure.openai.impl;

import com.alibaba.fastjson2.JSON;
import com.xufun.sdk.infrastructure.openai.IOpenAI;
import com.xufun.sdk.infrastructure.openai.dto.ChatCompletionRequestDTO;
import com.xufun.sdk.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;
import com.xufun.sdk.utils.BearerTokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 智谱 Chat Completions HTTP 客户端；调用前通过 BearerTokenUtils 签发短期 JWT。 */
public class ChatGLM implements IOpenAI {

    private static final Logger logger = LoggerFactory.getLogger(ChatGLM.class);

    private final String apiKeySecret;
    private final String apiHost;

    public ChatGLM(String apiHost, String apiKeySecret) {
        if (apiKeySecret == null || apiKeySecret.isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty");
        }
        if (apiHost == null || apiHost.isEmpty()) {
            throw new IllegalArgumentException("API host cannot be null or empty");
        }

        this.apiHost = apiHost;
        this.apiKeySecret = apiKeySecret;
    }

    @Override
    public ChatCompletionSyncResponseDTO completions(ChatCompletionRequestDTO requestDTO) throws Exception {
        String token = BearerTokenUtils.getToken(apiKeySecret);

        URL url = new URL(apiHost);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        );
        
        byte[] input = JSON.toJSONString(requestDTO).getBytes(StandardCharsets.UTF_8);

        try(OutputStream os = connection.getOutputStream()){
            os.write(input, 0, input.length);
            os.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            logger.warn("ChatGLM API HTTP status: {}", responseCode);
        }

        BufferedReader in;
        if (responseCode >= 200 && responseCode < 300) {
            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        } else {
            in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
        }
        
        String inputLine;
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
            content.append("\n");
        }

        in.close();
        connection.disconnect();

        return JSON.parseObject(content.toString(), ChatCompletionSyncResponseDTO.class);

    }
}
