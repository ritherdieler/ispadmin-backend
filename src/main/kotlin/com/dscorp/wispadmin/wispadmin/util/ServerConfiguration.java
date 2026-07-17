package com.dscorp.wispadmin.wispadmin.util;

import com.lyra.rest.client.ClientConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Configuration component that reads parameters from environment variables if exist
 *
 * @author Lyra Network
 */
@Configuration
public class ServerConfiguration {
    @Value("${custom.username}")
    private String username;

    @Value("${custom.password}")
    private String password;

    @Value("${custom.restApiServerName}")
    private String restApiServerName;

    @Value("${custom.hashKey}")
    private String hashKey;

    /*
     * Return a client configuration depending of chosen scenario
     */
    public ClientConfiguration getConfiguration() {
        ClientConfiguration.ClientConfigurationBuilder builder = ClientConfiguration.builder();

        if (!StringUtils.isEmpty(username)) {
            builder.username(username);
        }
        if (!StringUtils.isEmpty(password)) {
            builder.password(password);
        }
        if (!StringUtils.isEmpty(restApiServerName)) {
            builder.restApiServerName(restApiServerName);
        }
        if (!StringUtils.isEmpty(hashKey)) {
            builder.hashKey(hashKey);
        }

        return builder.build();
    }
}
