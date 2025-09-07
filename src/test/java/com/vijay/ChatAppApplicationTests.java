package com.vijay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "groq.api-key=test-key",
    "openai.api-key=test-key",
    "anthropic.api-key=test-key",
    "openrouter.api-key=test-key",
    "huggingface.api-key=test-key",
    "gemini.api-key=test-key"
})
class ChatAppApplicationTests {

	@Test
	void contextLoads() {
	}

}
