package com.railway.config;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.sms.SmsClient;
import com.azure.communication.sms.SmsClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureConfig {

    @Bean
    public BlobContainerClient blobContainer(@Value("${azure.storage.connection-string}") String cs,
                                             @Value("${azure.storage.container}") String container) {
        BlobContainerClient c = new BlobServiceClientBuilder().connectionString(cs)
                .buildClient().getBlobContainerClient(container);
        try { c.createIfNotExists(); } catch (Exception e) {
            System.err.println("WARN: Blob storage not reachable (is Azurite running?): " + e.getMessage());
        }
        return c;
    }

    @Bean
    @ConditionalOnExpression("!'${acs.connection-string:}'.isEmpty()")
    public SmsClient smsClient(@Value("${acs.connection-string}") String cs) {
        return new SmsClientBuilder().connectionString(cs).buildClient();
    }

    @Bean
    @ConditionalOnExpression("!'${acs.connection-string:}'.isEmpty()")
    public EmailClient emailClient(@Value("${acs.connection-string}") String cs) {
        return new EmailClientBuilder().connectionString(cs).buildClient();
    }
}
