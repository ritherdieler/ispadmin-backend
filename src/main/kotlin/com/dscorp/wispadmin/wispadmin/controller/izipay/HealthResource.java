package com.dscorp.wispadmin.wispadmin.controller.izipay;

import com.dscorp.wispadmin.wispadmin.util.ServerConfiguration;
import com.lyra.rest.client.Client;
import com.lyra.rest.client.ClientResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

/**
 * Rest resource that implements health operation.
 * <p></p>
 * This kind resource allows to check if the system is up un running normally
 *
 * @author Lyra Network
 */
@RestController
@RequestMapping("/izipay")

public class HealthResource {
    private final ServerConfiguration configuration;

    @Autowired
    public HealthResource(ServerConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * It calls the SDKTest resource of payment Rest API. This operation should just return an echo of the request
     * payload. It allows that verify that the connection with the remote server is ok.
     *
     * @return String JSON string representation with the health request result
     */
    @RequestMapping(path = "/health", method = GET)
    public String createPayment() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("value", "health status: OK");
        return Client.post(ClientResource.SDK_TEST.toString(), parameters, configuration.getConfiguration());
    }
}
