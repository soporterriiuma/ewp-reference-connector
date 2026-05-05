package eu.erasmuswithoutpaper.imobility.boundary;

import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.imobilities.endpoints.ImobilitiesGetResponse;
import eu.erasmuswithoutpaper.common.boundary.ClientRequest;
import eu.erasmuswithoutpaper.common.boundary.ClientResponse;
import eu.erasmuswithoutpaper.common.boundary.HttpMethodEnum;
import eu.erasmuswithoutpaper.common.boundary.ParamsClass;
import eu.erasmuswithoutpaper.common.control.RegistryClient;
import eu.erasmuswithoutpaper.common.control.RestClient;
import eu.erasmuswithoutpaper.security.InternalAuthenticate;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Stateless
@Path("imobility")
public class GuiIncomingMobilityResource {
    @Inject
    RegistryClient registryClient;

    @Inject
    RestClient restClient;

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(GuiIncomingMobilityResource.class.getCanonicalName());

    @GET
    @Path("get")
    @InternalAuthenticate
    public Response get(@QueryParam("hei_id") String heiId, @QueryParam("omobility_id") String omobilityId, @QueryParam("type") String type) {
        LOG.fine("get: Hei searched: " + heiId);

        Map<String, String> heiUrls = registryClient.getImobilitiesHeiUrls(heiId);
        if (heiUrls == null || heiUrls.isEmpty()) {
            LOG.fine("get: Hei not found: " + heiId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, String> entry : heiUrls.entrySet()) {
            LOG.fine("get: Hei URL: " + entry.getKey() + " -> " + entry.getValue());
        }
        String heiUrl = heiUrls.get("get-url");
        if (heiUrl == null || heiUrl.isEmpty()) {
            LOG.fine("get: Hei URL not found for: " + heiId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("get: Hei URL found: " + heiUrl);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(heiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(heiUrl);
        Map<String, List<String>> paramsMap = new HashMap<>();
        if (omobilityId != null && !omobilityId.isEmpty()) {
            paramsMap.put("omobility_id", Collections.singletonList(omobilityId));
        } else {
            LOG.fine("get: omobility_id is empty");
        }
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);
        LOG.fine("get: Params: " + paramsMap);
        ClientResponse iiaResponse = restClient.sendRequest(clientRequest, ImobilitiesGetResponse.class);

        GenericEntity<ImobilitiesGetResponse> entity = null;
        try {
            ImobilitiesGetResponse index = (ImobilitiesGetResponse) iiaResponse.getResult();
            entity = new GenericEntity<ImobilitiesGetResponse>(index) {
            };
        } catch (Exception e) {
            return Response.serverError().entity(iiaResponse.getErrorMessage()).build();
        }

        if ("xml".equalsIgnoreCase(type)) {
            return Response.ok(entity)
                    .type(MediaType.APPLICATION_XML)
                    .build();
        } else {
            return Response.ok(entity)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @POST
    @Path("send-cnr")
    @InternalAuthenticate
    public Response sendCnr(@QueryParam("hei_id") String heiId, @QueryParam("omobility_id") String omobilityId, @QueryParam("type") String type) {
        LOG.fine("CNR: start");
        if (heiId == null || heiId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing hei_id").build();
        }
        if (omobilityId == null || omobilityId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing omobility_id").build();
        }

        Map<String, String> urls = registryClient.getImobilitiesCnrHeiUrls(heiId);
        if (urls == null || urls.isEmpty()) {
            LOG.fine("CNR: No CNR URLs found for HEI " + heiId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, String> entry : urls.entrySet()) {
            LOG.fine("CNR: Found CNR URL for HEI " + heiId + ": " + entry.getKey() + " -> " + entry.getValue());
            ClientRequest clientRequest = new ClientRequest();
            clientRequest.setUrl(entry.getValue());
            clientRequest.setHeiId(heiId);
            clientRequest.setMethod(HttpMethodEnum.POST);
            clientRequest.setHttpsec(true);

            Map<String, List<String>> paramsMap = new HashMap<>();
            paramsMap.put("omobility_id", Collections.singletonList(omobilityId));
            ParamsClass paramsClass = new ParamsClass();
            paramsClass.setUnknownFields(paramsMap);
            clientRequest.setParams(paramsClass);

            ClientResponse cnrResponse = restClient.sendRequest(clientRequest, Empty.class);
            LOG.fine("CNR: response: " + cnrResponse.getRawResponse());
        }

        if ("xml".equalsIgnoreCase(type)) {
            return Response.ok().type(MediaType.APPLICATION_XML).build();
        } else {
            return Response.ok().type(MediaType.APPLICATION_JSON).build();
        }
    }
}
