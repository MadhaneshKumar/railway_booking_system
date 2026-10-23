package com.railway.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Created only when KEYVAULT_URI is set. Uses managed identity in Azure, `az login` locally. */
@Configuration
@ConditionalOnExpression("!'${azure.keyvault.uri:}'.isEmpty()")
public class KeyVaultConfig {
    @Bean
    public SecretClient secretClient(@Value("${azure.keyvault.uri}") String uri) {
        return new SecretClientBuilder().vaultUrl(uri)
                .credential(new DefaultAzureCredentialBuilder().build()).buildClient();
    }
}
