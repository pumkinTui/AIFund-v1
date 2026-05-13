package com.fund.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

@SpringBootApplication
@EnableScheduling //启用定时任务
public class FundValuationAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(FundValuationAssistantApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        // 东方财富 push2 接口反爬升级，统一给所有 RestTemplate 请求套浏览器壳
        // 定义请求拦截器：统一为所有 HTTP 请求设置请求头
        ClientHttpRequestInterceptor browserInterceptor = (request, body, execution) -> {
            // 请求头封装：完全模拟 Chrome 浏览器访问
            request.getHeaders().set("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            // Referer：声明请求来源为东方财富行情页，提高请求可信度
            request.getHeaders().set("Referer", "https://quote.eastmoney.com/");
            // 接受所有类型响应
            request.getHeaders().set("Accept", "*/*");
            // 语言设置：中文
            request.getHeaders().set("Accept-Language", "zh-CN,zh;q=0.9");
            // 保持长连接，模拟真实浏览器
            request.getHeaders().set("Connection", "keep-alive");
            // 执行请求
            return execution.execute(request, body);
        };
        // 将拦截器设置到 RestTemplate 中，全局生效
        restTemplate.setInterceptors(Collections.singletonList(browserInterceptor));
        return restTemplate;
    }

}
