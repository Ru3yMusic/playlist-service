package com.rubymusic.playlist.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** Base64-encoded X.509 RSA public key (strip PEM headers before encoding). */
    private String publicKey;
}
