package eu.erasmuswithoutpaper.iia.common;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.erasmuswithoutpaper.imobility.control.IncomingMobilityConverter;

public class MessageNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(IncomingMobilityConverter.class);

    public static Response addApprovalNotification(String url, String msg, String token) {
        return postJson(url, null, msg, token);
    }

    public static Response addNotification(String url, Map<String, String> urlParams, String msg, String token) {
        return postJson(url, urlParams, msg, token);
    }

    private static Response postJson(String url, Map<String, String> urlParams, String msg, String token) {
        ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        Client client = clientBuilder.build();
        Response response = null;

        try {
            WebTarget target = client.target(url);
            if (urlParams != null) {
                for (Map.Entry<String, String> entry : urlParams.entrySet()) {
                    target = target.queryParam(entry.getKey(), entry.getValue());
                }
            }

            Invocation.Builder postBuilder = target.request().header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_TYPE);
            postBuilder = postBuilder.header("Authorization", token);

            response = postBuilder.post(Entity.json(new String(msg.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));

            int status = response.getStatus();
            String body = response.hasEntity() ? response.readEntity(String.class) : "";

            logger.info("Response status: " + status);

            MultivaluedMap<String, Object> responseHeaders = response.getHeaders();
            logger.info("Response headers:");
            responseHeaders.forEach((key, values) -> logger.info(key + ": " + values));

            logger.info("Response body: " + body);
            return Response.status(status).entity(body).build();
        } finally {
            if (response != null) {
                response.close();
            }
            client.close();
        }
    }
}
