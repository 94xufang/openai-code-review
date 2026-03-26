package com.xufun.sdk.infrastructure.openai;

import com.xufun.sdk.infrastructure.openai.dto.ChatCompletionRequestDTO;
import com.xufun.sdk.infrastructure.openai.dto.ChatCompletionSyncResponseDTO;

public interface IOpenAI {
    ChatCompletionSyncResponseDTO completions(ChatCompletionRequestDTO requestDTO) throws Exception;
}
