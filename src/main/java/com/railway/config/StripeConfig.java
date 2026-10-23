package com.railway.config;

import com.azure.security.keyvault.secrets.SecretClient;
import com.stripe.Stripe;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {
    public StripeConfig(@Value("${stripe.secret-key:}") String key, ObjectProvider<SecretClient> keyVault) {
        String k = key;
        SecretClient kv = keyVault.getIfAvailable();
        if ((k == null || k.isBlank()) && kv != null) {
            k = kv.getSecret("stripe-secret-key").getValue();   // secret name in Key Vault
        }
        Stripe.apiKey = k;
    }
}
