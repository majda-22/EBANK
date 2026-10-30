package com.ebank.customer.config;

import java.util.Arrays;
import com.ebank.customer.mcp.CustomerTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Slf4j
public class CustomerMcpConfiguration {
    @Bean
    public ToolCallbackProvider customerToolCallbackProvider(CustomerTools tools) {
        var provider = MethodToolCallbackProvider.builder().toolObjects(tools).build();
        log.info("Registered customer MCP tools: {}", Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name()).sorted().toList());
        return provider;
    }
}
