package eu.erasmuswithoutpaper.omobility.boundary;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.omobilities.endpoints.OmobilitiesGetResponse;
import eu.erasmuswithoutpaper.api.omobilities.endpoints.OmobilitiesIndexResponse;
import eu.erasmuswithoutpaper.api.omobilities.endpoints.OmobilitiesUpdateRequest;
import eu.erasmuswithoutpaper.api.omobilities.endpoints.OmobilitiesUpdateResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasGetResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasIndexResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasUpdateRequest;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasUpdateResponse;
import eu.erasmuswithoutpaper.common.boundary.ClientRequest;
import eu.erasmuswithoutpaper.common.boundary.ClientResponse;
import eu.erasmuswithoutpaper.common.boundary.HttpMethodEnum;
import eu.erasmuswithoutpaper.common.boundary.ParamsClass;
import eu.erasmuswithoutpaper.common.control.HeiEntry;
import eu.erasmuswithoutpaper.common.control.RegistryClient;
import eu.erasmuswithoutpaper.common.control.RestClient;
import eu.erasmuswithoutpaper.monitoring.SendMonitoringService;
import eu.erasmuswithoutpaper.omobility.entity.Mobility;
import eu.erasmuswithoutpaper.omobility.entity.MobilityStatus;
import eu.erasmuswithoutpaper.omobility.las.boundary.GuiOutgoingMobilityLearningAgreementsResourceREAL;
import eu.erasmuswithoutpaper.security.InternalAuthenticate;

@Stateless
@Path("omobility")
public class GuiOutgoingMobilityResource {
    @Inject
    RegistryClient registryClient;

    @Inject
    RestClient restClient;

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(GuiOutgoingMobilityResource.class.getCanonicalName());

    @GET
    @Path("index")
    @InternalAuthenticate
    public Response index(@QueryParam("hei_id") String heiId, @QueryParam("receiving_academic_year_id") String receivingAcademicYearId, @QueryParam("global_id") String globalId, @QueryParam("activity_attributes") String activityAttributes, @QueryParam("modified_since") String modifiedSince, @QueryParam("type") String type) {
        LOG.fine("index: Hei searched: " + heiId);

        Map<String, String> heiUrls = registryClient.getOmobilitiesHeiUrls(heiId);
        if (heiUrls == null || heiUrls.isEmpty()) {
            LOG.fine("index: Hei not found: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, String> entry : heiUrls.entrySet()) {
            LOG.fine("index: Hei URL: " + entry.getKey() + " -> " + entry.getValue());
        }
        String heiUrl = heiUrls.get("index-url");
        if (heiUrl == null || heiUrl.isEmpty()) {
            LOG.fine("index: Hei URL not found for: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("index: Hei URL found: " + heiUrl);

        String receiving_academic_year_id_processed = processAcademicYearId(receivingAcademicYearId);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(heiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(heiUrl);
        Map<String, List<String>> paramsMap = new HashMap<>();
        if (receiving_academic_year_id_processed != null && !receiving_academic_year_id_processed.isEmpty()) {
            paramsMap.put("receiving_academic_year_id", Collections.singletonList(receiving_academic_year_id_processed));
        } else {
            LOG.fine("index: receiving_academic_year_id is empty or invalid");
        }
        if (globalId != null && !globalId.isEmpty()) {
            paramsMap.put("global_id", Collections.singletonList(globalId));
        } else {
            LOG.fine("index: global_id is empty");
        }
        if (activityAttributes != null && !activityAttributes.isEmpty()) {
            paramsMap.put("activity_attributes", Collections.singletonList(activityAttributes));
        } else {
            LOG.fine("index: activity_attributes is empty");
        }
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);
        LOG.fine("index: Params: " + paramsMap);
        ClientResponse iiaResponse = restClient.sendRequest(clientRequest, OmobilitiesIndexResponse.class);

        GenericEntity<OmobilitiesIndexResponse> entity = null;
        try {
            OmobilitiesIndexResponse index = (OmobilitiesIndexResponse) iiaResponse.getResult();
            entity = new GenericEntity<OmobilitiesIndexResponse>(index) {
            };
        } catch (Exception e) {
            return javax.ws.rs.core.Response.serverError().entity(iiaResponse.getErrorMessage()).build();
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

    @GET
    @Path("get")
    @InternalAuthenticate
    public Response get(@QueryParam("hei_id") String heiId, @QueryParam("omobility_id") String omobilityId, @QueryParam("type") String type) {
        LOG.fine("get: Hei searched: " + heiId);

        Map<String, String> heiUrls = registryClient.getOmobilitiesHeiUrls(heiId);
        if (heiUrls == null || heiUrls.isEmpty()) {
            LOG.fine("get: Hei not found: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, String> entry : heiUrls.entrySet()) {
            LOG.fine("get: Hei URL: " + entry.getKey() + " -> " + entry.getValue());
        }
        String heiUrl = heiUrls.get("get-url");
        if (heiUrl == null || heiUrl.isEmpty()) {
            LOG.fine("get: Hei URL not found for: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
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
        ClientResponse iiaResponse = restClient.sendRequest(clientRequest, OmobilitiesGetResponse.class);

        GenericEntity<OmobilitiesGetResponse> entity = null;
        try {
            OmobilitiesGetResponse index = (OmobilitiesGetResponse) iiaResponse.getResult();
            entity = new GenericEntity<OmobilitiesGetResponse>(index) {
            };
        } catch (Exception e) {
            return javax.ws.rs.core.Response.serverError().entity(iiaResponse.getErrorMessage()).build();
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
    @Path("update/approve")
    @InternalAuthenticate
    public javax.ws.rs.core.Response approve(@QueryParam("hei_id") String heiId, @QueryParam("type") String type, OmobilitiesUpdateRequest updateRequest) {
        LOG.fine("APPROVE: start");

        if (updateRequest.getApproveProposalV1() == null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (updateRequest.getRejectProposalV1() != null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Map<String, String> map = registryClient.getOmobilitiesHeiUrls(heiId);
        if (map == null || map.isEmpty()) {
            LOG.fine("APPROVE: Hei not found: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }
        LOG.fine("APPROVE: map: " + map.toString());

        String url = map.get("update-url");
        if (url == null || url.isEmpty()) {
            LOG.fine("APPROVE: Hei URL not found for: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }
        LOG.fine("APPROVE: upd url: " + url);

        /*ClientResponse hash = sendRequestOwn(omobilityLasUpdateRequest);

        String hashString = (String) hash.getResult();

        LOG.fine("APPROVE: hash: " + hashString);*/

        ClientResponse response = sendRequest(updateRequest, url);

        LOG.fine("APPROVE: response: " + response.getRawResponse());

        if (response.getStatusCode() != Response.Status.OK.getStatusCode()) {
            return Response.status(response.getStatusCode()).entity(response.getErrorMessage()).build();
        }

        OmobilitiesUpdateResponse omobilityLasUpdateResponse = (OmobilitiesUpdateResponse) response.getResult();

        if ("xml".equalsIgnoreCase(type)) {
            return Response.ok(omobilityLasUpdateResponse)
                    .type(MediaType.APPLICATION_XML)
                    .build();
        } else {
            return Response.ok(omobilityLasUpdateResponse)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @POST
    @Path("update/reject")
    @InternalAuthenticate
    public javax.ws.rs.core.Response reject(@QueryParam("hei_id") String heiId, @QueryParam("type") String type, OmobilitiesUpdateRequest updateRequest) {
        LOG.fine("REJCET: start");

        if (updateRequest.getApproveProposalV1() != null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (updateRequest.getRejectProposalV1() == null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Map<String, String> map = registryClient.getOmobilitiesHeiUrls(heiId);
        if (map == null || map.isEmpty()) {
            LOG.fine("REJCET: Hei not found: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }
        LOG.fine("REJCET: map: " + map.toString());
        String url = map.get("update-url");
        if (url == null || url.isEmpty()) {
            LOG.fine("REJCET: Hei URL not found for: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }
        LOG.fine("REJCET: upd url: " + url);

        /*ClientResponse hash = sendRequestOwn(omobilityLasUpdateRequest);

        String hashString = (String) hash.getResult();

        LOG.fine("REJCET: hash: " + hashString);*/

        ClientResponse response = sendRequest(updateRequest, url);

        LOG.fine("REJCET: response: " + response.getRawResponse());

        if (response.getStatusCode() != Response.Status.OK.getStatusCode()) {
            return Response.status(response.getStatusCode()).entity(response.getErrorMessage()).build();
        }

        OmobilitiesUpdateResponse omobilityLasUpdateResponse = (OmobilitiesUpdateResponse) response.getResult();

        if ("xml".equalsIgnoreCase(type)) {
            return Response.ok(omobilityLasUpdateResponse)
                    .type(MediaType.APPLICATION_XML)
                    .build();
        } else {
            return Response.ok(omobilityLasUpdateResponse)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @POST
    @Path("send-cnr")
    @InternalAuthenticate
    public javax.ws.rs.core.Response sendCnr(@QueryParam("hei_id") String heiId, @QueryParam("omobility_id") String omobilityId, @QueryParam("type") String type) {
        LOG.fine("CNR: start");
        if (heiId == null || heiId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing hei_id").build();
        }
        if (omobilityId == null || omobilityId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing omobility_id").build();
        }

        Map<String, String> urls = registryClient.getOmobilitiesCnrHeiUrls(heiId);
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

    private String processAcademicYearId(String receivingAcademicYearId) {
        LOG.fine("processAcademicYearId: Processing academic year id: " + (receivingAcademicYearId == null ? "null" : receivingAcademicYearId));
        try {
            if (receivingAcademicYearId == null || receivingAcademicYearId.isEmpty()) {
                return null;
            }

            if(receivingAcademicYearId.contains("/")){
                return  receivingAcademicYearId;
            } else if (receivingAcademicYearId.length() == 4) {
                String endYear = String.valueOf(Integer.parseInt(receivingAcademicYearId) + 1);
                return receivingAcademicYearId + "/" + endYear;
            } else {
                return null;
            }
        } catch (Exception e) {
            LOG.fine("processAcademicYearId: Error processing academic year id: " + receivingAcademicYearId);
            return null;
        }
    }

    private ClientResponse sendRequest(OmobilitiesUpdateRequest omobilitiesUpdateRequest, String url) {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setUrl(url);
        clientRequest.setMethod(HttpMethodEnum.POST);
        clientRequest.setHttpsec(true);
        clientRequest.setXml(omobilitiesUpdateRequest);

        return restClient.sendRequest(clientRequest, OmobilitiesUpdateResponse.class, true);
    }
}
