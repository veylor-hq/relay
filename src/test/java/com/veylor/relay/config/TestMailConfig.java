package com.veylor.relay.config;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
public class TestMailConfig {

    @Bean
    @Primary
    public JavaMailSender mockJavaMailSender() {
        return Mockito.mock(JavaMailSender.class);
    }
}
