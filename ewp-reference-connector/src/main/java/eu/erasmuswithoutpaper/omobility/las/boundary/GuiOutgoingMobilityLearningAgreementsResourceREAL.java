package eu.erasmuswithoutpaper.omobility.las.boundary;

import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.iias.endpoints.IiasGetResponse;
import eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.*;
import eu.erasmuswithoutpaper.common.boundary.ClientRequest;
import eu.erasmuswithoutpaper.common.boundary.ClientResponse;
import eu.erasmuswithoutpaper.common.boundary.HttpMethodEnum;
import eu.erasmuswithoutpaper.common.boundary.ParamsClass;
import eu.erasmuswithoutpaper.common.control.RegistryClient;
import eu.erasmuswithoutpaper.common.control.RestClient;
import eu.erasmuswithoutpaper.iia.boundary.NotifyAux;
import eu.erasmuswithoutpaper.monitoring.SendMonitoringService;
import eu.erasmuswithoutpaper.omobility.las.control.LearningAgreementEJB;
import eu.erasmuswithoutpaper.omobility.las.control.OmobilityLasConverters;
import eu.erasmuswithoutpaper.omobility.las.control.OutgoingMobilityLearningAgreementsConverter;
import eu.erasmuswithoutpaper.omobility.las.dto.OmobilityLasUpdateRequestDto;
import eu.erasmuswithoutpaper.omobility.las.dto.SyncReturnDTO;
import eu.erasmuswithoutpaper.omobility.las.entity.MobilityInstitution;
import eu.erasmuswithoutpaper.omobility.las.entity.OlearningAgreement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Path("omobilities/las")
public class GuiOutgoingMobilityLearningAgreementsResourceREAL {

    private static final Logger log = LoggerFactory.getLogger(GuiOutgoingMobilityLearningAgreementsResourceREAL.class);
    @EJB
    LearningAgreementEJB learningAgreementEJB;

    @Inject
    OutgoingMobilityLearningAgreementsConverter converter;

    @Inject
    RegistryClient registryClient;

    @Inject
    RestClient restClient;

    @Inject
    SendMonitoringService sendMonitoringService;

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(GuiOutgoingMobilityLearningAgreementsResourceREAL.class.getCanonicalName());

    @GET
    @Path("")
    public Response hello(@QueryParam("sending_hei_id") String sendingHeiId, @QueryParam("omobility_id") List<String> mobilityIdList) {
        OmobilityLasGetResponse response = new OmobilityLasGetResponse();
        List<OlearningAgreement> omobilityLasList = learningAgreementEJB.findBySendingHeiIdFilterd(sendingHeiId);

        if (!omobilityLasList.isEmpty()) {

            response.getLa().addAll(omobilitiesLas(omobilityLasList, mobilityIdList));
        }

        return Response.ok(response).build();
    }

    @GET
    @Path("get")
    @Produces("application/json")
    public Response get(@QueryParam("id") String id) {
        LOG.fine("DOWNLOAD: start");
        LOG.fine("DOWNLOAD: id: " + id);

        OlearningAgreement olearningAgreement = learningAgreementEJB.findById(id);
        if (olearningAgreement == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LearningAgreement learningAgreement = converter.convertToLearningAgreements(olearningAgreement);
        OlearningAgreement olearningAgreementDTO = converter.convertToOlearningAgreement(learningAgreement, false, olearningAgreement);

        return Response.ok(olearningAgreementDTO).build();
    }

    @POST
    @Path("create")
    @Consumes("application/json")
    public Response create(LearningAgreement learningAgreementDTO) {

        LOG.fine("CREATE: start");
        OlearningAgreement olearningAgreement = converter.convertToOlearningAgreement(learningAgreementDTO, false, null);

        //olearningAgreement.setEqfLevelStudiedAtDeparture(Byte.parseByte("6"));
        olearningAgreement.setFromPartner(false);
        String id = learningAgreementEJB.insert(olearningAgreement);
        olearningAgreement.setId(id);

        LOG.fine("CREATE: olearningAgreement: " + olearningAgreement.getId());
        LOG.fine("CREATE: olearningAgreement chengeproposalId: " + olearningAgreement.getChangesProposal().getId());

        LOG.fine("CREATE: Send notification");

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyPartner(olearningAgreement);
        });

        LOG.fine("CREATE: notification sent");

        return Response.ok(id).build();
    }


    @POST
    @Path("change")
    @Consumes("application/json")
    public Response change(LearningAgreement learningAgreement) {
        LOG.fine("CHANGE: start");

        LOG.fine("CHANGE: olearningAgreement: " + learningAgreement.getOmobilityId());

        OlearningAgreement original = learningAgreementEJB.findById(learningAgreement.getOmobilityId());
        if (original == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        OlearningAgreement olearningAgreement = converter.convertToOlearningAgreement(learningAgreement, false, original);
        //olearningAgreement.setEqfLevelStudiedAtDeparture(Byte.parseByte("6"));
        olearningAgreement.setFromPartner(false);
        String id = learningAgreementEJB.update(olearningAgreement);

        if (id == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyPartner(olearningAgreement);
        });

        LOG.fine("CHANGE: merge olearningAgreement: " + olearningAgreement.getId());

        return Response.ok(id).build();
    }

    @POST
    @Path("update/approve")
    @Consumes(MediaType.APPLICATION_XML)
    public Response updateAccept(OmobilityLasUpdateRequestDto omobilityLasUpdateRequestDto) throws Exception {
        /*if (omobilityLasUpdateRequest.getApproveProposalV1() != null && omobilityLasUpdateRequest.getApproveProposalV1().getSignature() != null) {
            GregorianCalendar calendar = new GregorianCalendar();

            // Set the desired timezone (e.g., +01:00)
            TimeZone timeZone = TimeZone.getTimeZone("Europe/Paris"); // Change as needed
            calendar.setTimeZone(timeZone);

            omobilityLasUpdateRequest.getApproveProposalV1().getSignature().setTimestamp(DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar));
        }*/
        OmobilityLasUpdateRequest omobilityLasUpdateRequest = OmobilityLasConverters.fromDto(omobilityLasUpdateRequestDto);
        LOG.fine("APPROVE: start");
        LOG.fine("APPROVE request: " + omobilityLasUpdateRequest.toString());

        Map<String, String> map = registryClient.getOmobilityLasHeiUrls(omobilityLasUpdateRequest.getSendingHeiId());
        LOG.fine("APPROVE: map: " + map.toString());
        String url = map.get("update-url");
        LOG.fine("APPROVE: upd url: " + url);

        ClientResponse hash = sendRequestOwn(omobilityLasUpdateRequest);

        String hashString = (String) hash.getResult();

        LOG.fine("APPROVE: hash: " + hashString);

        ClientResponse response = sendRequest(omobilityLasUpdateRequest, url, hashString);

        LOG.fine("APPROVE: response: " + response.getRawResponse());

        if (response.getStatusCode() != Response.Status.OK.getStatusCode()) {
            return Response.status(response.getStatusCode()).entity(response.getErrorMessage()).build();
        }

        OmobilityLasUpdateResponse omobilityLasUpdateResponse = (OmobilityLasUpdateResponse) response.getResult();

        return Response.ok(omobilityLasUpdateResponse).build();
    }

    @POST
    @Path("update/reject")
    @Consumes(MediaType.APPLICATION_XML)
    public Response updateReject(OmobilityLasUpdateRequestDto omobilityLasUpdateRequestDto) throws JAXBException, IOException, DatatypeConfigurationException {
        /*if (omobilityLasUpdateRequest.getCommentProposalV1() != null && omobilityLasUpdateRequest.getCommentProposalV1().getSignature() != null) {
            GregorianCalendar calendar = new GregorianCalendar();

            // Set the desired timezone (e.g., +01:00)
            TimeZone timeZone = TimeZone.getTimeZone("Europe/Paris"); // Change as needed
            calendar.setTimeZone(timeZone);

            omobilityLasUpdateRequest.getCommentProposalV1().getSignature().setTimestamp(DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar));
        }*/
        OmobilityLasUpdateRequest omobilityLasUpdateRequest = OmobilityLasConverters.fromDto(omobilityLasUpdateRequestDto);
        LOG.fine("REJCET: start");
        LOG.fine("REJCET request: " + omobilityLasUpdateRequest.toString());

        Map<String, String> map = registryClient.getOmobilityLasHeiUrls(omobilityLasUpdateRequest.getSendingHeiId());
        LOG.fine("REJCET: map: " + map.toString());
        String url = map.get("update-url");
        LOG.fine("REJCET: upd url: " + url);

        ClientResponse hash = sendRequestOwn(omobilityLasUpdateRequest);

        String hashString = (String) hash.getResult();

        LOG.fine("REJCET: hash: " + hashString);

        ClientResponse response = sendRequest(omobilityLasUpdateRequest, url, hashString);

        LOG.fine("REJCET: response: " + response.getRawResponse());

        if (response.getStatusCode() != Response.Status.OK.getStatusCode()) {
            return Response.status(response.getStatusCode()).entity(response.getErrorMessage()).build();
        }

        OmobilityLasUpdateResponse omobilityLasUpdateResponse = (OmobilityLasUpdateResponse) response.getResult();

        return Response.ok(omobilityLasUpdateResponse).build();
        /*
        LOG.fine("REJCET: start");
        LOG.fine("REJCET: ownId: " + id);
        LOG.fine("REJCET request: " + omobilityLasUpdateRequest.toString());

        learningAgreementEJB.rejectChangesProposal(omobilityLasUpdateRequest, id);

        Map<String, String> map = registryClient.getOmobilityLasHeiUrls(omobilityLasUpdateRequest.getSendingHeiId());
        LOG.fine("REJCET: map: " + map.toString());
        String url = map.get("update-url");
        LOG.fine("REJCET: upd url: " + url);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setUrl(url);
        clientRequest.setMethod(HttpMethodEnum.POST);
        clientRequest.setHttpsec(true);
        clientRequest.setXml(omobilityLasUpdateRequest);

        ClientResponse response = restClient.sendRequest(clientRequest, Empty.class, true, toXml(omobilityLasUpdateRequest));

        LOG.fine("REJCET: response: " + response.getRawResponse());

        return Response.ok(response).build();*/
    }

    private ClientResponse sendRequest(OmobilityLasUpdateRequest omobilityLasUpdateRequest, String url, String hash) {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setUrl(url);
        clientRequest.setMethod(HttpMethodEnum.POST);
        clientRequest.setHttpsec(true);
        clientRequest.setXml(omobilityLasUpdateRequest);

        return restClient.sendRequest(clientRequest, OmobilityLasUpdateResponse.class, true, hash);
    }

    private ClientResponse sendRequestOwn(OmobilityLasUpdateRequest omobilityLasUpdateRequest) {
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setUrl("https://localhost/algoria/omobilities/las/test/digest");
        clientRequest.setMethod(HttpMethodEnum.POST);
        clientRequest.setHttpsec(true);
        clientRequest.setXml(omobilityLasUpdateRequest);

        return restClient.sendRequestOwn(clientRequest);
    }

    @GET
    @Path("XML")
    @Consumes("application/xml")
    public Response getXML(@QueryParam("id") String id) {

        LOG.fine("XML: start");

        OlearningAgreement olearningAgreement = learningAgreementEJB.findById(id);
        OmobilityLasGetResponse response = new OmobilityLasGetResponse();
        response.getLa().add(converter.convertToLearningAgreements(olearningAgreement));

        return Response.ok(response).build();
    }

    @POST
    @Path("sendCNR")
    public Response sendCNR(@QueryParam("id") String id) {
        LOG.fine("sendCNR: start");
        OlearningAgreement olearningAgreement = learningAgreementEJB.findById(id);
        if (olearningAgreement == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        notifyPartner(olearningAgreement);

        return Response.ok().build();
    }

    private List<ClientResponse> notifyPartner(OlearningAgreement olearningAgreement) {
        LOG.fine("NOTIFY: Send notification");

        String localHeiId = learningAgreementEJB.getHeiId();

        List<ClientResponse> partnersResponseList = new ArrayList<>();

        Set<NotifyAux> cnrUrls = new HashSet<>();

        MobilityInstitution partnerSending = olearningAgreement.getSendingHei();
        MobilityInstitution partnerReceiving = olearningAgreement.getReceivingHei();

        LOG.fine("NOTIFY: partnerSending: " + partnerSending.getHeiId());
        LOG.fine("NOTIFY: partnerReceiving: " + partnerReceiving.getHeiId());

        Map<String, String> urls = null;

        if (!localHeiId.equals(partnerSending.getHeiId())) {

            //Get the url for notify the institute not supported by our EWP
            urls = registryClient.getOmobilityLaCnrHeiUrls(partnerSending.getHeiId());

            if (urls != null) {
                for (Map.Entry<String, String> entry : urls.entrySet()) {
                    cnrUrls.add(new NotifyAux(partnerSending.getHeiId(), entry.getValue()));
                }
            }

        }
        if (!localHeiId.equals(partnerReceiving.getHeiId())) {

            //Get the url for notify the institute not supported by our EWP
            urls = registryClient.getOmobilityLaCnrHeiUrls(partnerReceiving.getHeiId());

            if (urls != null) {
                for (Map.Entry<String, String> entry : urls.entrySet()) {
                    cnrUrls.add(new NotifyAux(partnerReceiving.getHeiId(), entry.getValue()));
                }

            }
        }


        String finalLocalHeiId = localHeiId;
        cnrUrls.forEach(url -> {
            LOG.fine("NOTIFY: url: " + url.getUrl());
            LOG.fine("NOTIFY: heiId: " + url.getHeiId());
            //Notify the other institution about the modification
            ClientRequest clientRequest = new ClientRequest();
            clientRequest.setUrl(url.getUrl());//get the first and only one url
            clientRequest.setHeiId(url.getHeiId());
            clientRequest.setMethod(HttpMethodEnum.POST);
            clientRequest.setHttpsec(true);

            Map<String, List<String>> paramsMap = new HashMap<>();
            paramsMap.put("sending_hei_id", Collections.singletonList(finalLocalHeiId));
            paramsMap.put("omobility_id", Collections.singletonList(olearningAgreement.getId()));
            ParamsClass paramsClass = new ParamsClass();
            paramsClass.setUnknownFields(paramsMap);
            clientRequest.setParams(paramsClass);

            ClientResponse iiaResponse = restClient.sendRequest(clientRequest, Empty.class);

            LOG.fine("NOTIFY: response: " + iiaResponse.getRawResponse());

            try {
                if (iiaResponse.getStatusCode() <= 599 && iiaResponse.getStatusCode() >= 400) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "ola-cnr", null, Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), null);
                } else if (iiaResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "ola-cnr", null, Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), "Error");
                }
            } catch (Exception e) {

            }

            partnersResponseList.add(iiaResponse);
        });

        return partnersResponseList;

    }

    private List<LearningAgreement> omobilitiesLas(List<OlearningAgreement> omobilityLasList, List<String> omobilityLasIdList) {
        List<LearningAgreement> omobilitiesLas = new ArrayList<>();
        if (omobilityLasIdList == null || omobilityLasIdList.isEmpty()) {
            omobilityLasList.stream().forEachOrdered((m) -> {
                omobilitiesLas.add(converter.convertToLearningAgreements(m));
            });
            return omobilitiesLas;
        }
        omobilityLasList.stream().forEachOrdered((m) -> {
            if (omobilityLasIdList.contains(m.getId()) || omobilityLasIdList.contains(m.getOmobilityId())) {
                omobilitiesLas.add(converter.convertToLearningAgreements(m));
            }
        });

        return omobilitiesLas;
    }

    @POST
    @Path("digest")
    public Response computeDigest(String body) {
        try {
            LOG.fine("Received body:\n" + body);

            // Compute SHA-256 Digest
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] binaryDigest = digest.digest(body.getBytes(StandardCharsets.UTF_8));

            // Encode to Base64
            String base64Digest = Base64.getEncoder().encodeToString(binaryDigest);

            // Return the computed digest as a plain text response
            return Response.ok(base64Digest).build();

        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error computing digest: " + e.getMessage()).build();
        }
    }

    @GET
    @Path("getComapre")
    public Response ownGet(@QueryParam("id") String id) {

        OlearningAgreement olearningAgreement = learningAgreementEJB.findById(id);

        Map<String, String> map = registryClient.getOmobilityLasHeiUrls(olearningAgreement.getSendingHei().getHeiId());
        LOG.fine("getComapre: map: " + (map == null ? "null" : map.toString()));
        if (map == null || map.isEmpty()) {
            LOG.fine("getComapre: No LAS URLs found for HEI " + olearningAgreement.getSendingHei().getHeiId());
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String url = map.get("get-url");

        ClientResponse response = sendRequestAux(olearningAgreement.getSendingHei().getHeiId(), id, "https://localhost/rest/omobilities/las/get", OmobilityLasGetResponse.class);
        ClientResponse response2 = sendRequestAux(olearningAgreement.getSendingHei().getHeiId(), olearningAgreement.getOmobilityId(), url, OmobilityLasGetResponse.class);

        log.info("getComapre: Response own: " + response.getRawResponse());
        log.info("getComapre: Response partner: " + response2.getRawResponse());

        OmobilityLasGetResponse a = (OmobilityLasGetResponse) response2.getResult();
        if (a.getLa() != null) {
            a.getLa().forEach(la -> {
                if (la.getChangesProposal() != null &&  la.getChangesProposal().getComponentsRecognized() != null && la.getChangesProposal().getComponentsRecognized().getComponent() != null) {
                    la.getChangesProposal().getComponentsRecognized().getComponent().forEach(component -> {
                        log.info("getComapre: component: " + component.getTitle());
                    });
                }
            });
        }

        return Response.ok().build();

    }

    private ClientResponse sendRequestAux(String sendingHeiId, String id, String url, Class<?> clazz) {
        Map<String, List<String>> map = new HashMap<>();
        map.put("sending_hei_id", Collections.singletonList(sendingHeiId));
        map.put("omobility_id", Collections.singletonList(id));

        log.info("sendRequestAux: url: " + url);
        log.info("sendRequestAux: map: " + map.toString());

        ParamsClass paramsClass = new ParamsClass();
        paramsClass.setUnknownFields(map);
        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setUrl( url);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setHttpsec(true);
        clientRequest.setParams(paramsClass);

        return restClient.sendRequest(clientRequest, clazz);
    }

    @GET
    @Path("index-partner")
    public Response indexPartner(@QueryParam("sending_hei_id") String sending_hei_id, @QueryParam("receiving_hei_id") String receiving_hei_id, @QueryParam("receiving_academic_year_id") String receiving_academic_year_id) {
        LOG.fine("index-partner: Hei searched: " + sending_hei_id);

        Map<String, String> heiUrls = registryClient.getOmobilityLasHeiUrls(sending_hei_id);
        if (heiUrls == null || heiUrls.isEmpty()) {
            LOG.fine("index-partner: Hei not found: " + sending_hei_id);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, String> entry : heiUrls.entrySet()) {
            LOG.fine("index-partner: Hei URL: " + entry.getKey() + " -> " + entry.getValue());
        }
        String heiUrl = heiUrls.get("index-url");
        if (heiUrl == null || heiUrl.isEmpty()) {
            LOG.fine("index-partner: Hei URL not found for: " + sending_hei_id);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("index-partner: Hei URL found: " + heiUrl);

        String receiving_academic_year_id_processed = processAcademicYearId(receiving_academic_year_id);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(sending_hei_id);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(heiUrl);
        Map<String, List<String>> paramsMap = new HashMap<>();
        if (sending_hei_id != null && !sending_hei_id.isEmpty()) {
            paramsMap.put("sending_hei_id", Collections.singletonList(sending_hei_id));
        } else {
            LOG.fine("index-partner: sending_hei_id is empty");
        }
        if (receiving_hei_id != null && !receiving_hei_id.isEmpty()) {
            paramsMap.put("receiving_hei_id", Collections.singletonList(receiving_hei_id));
        } else {
            LOG.fine("index-partner: receiving_hei_id is empty");
        }
        if (receiving_academic_year_id_processed != null && !receiving_academic_year_id_processed.isEmpty()) {
            paramsMap.put("receiving_academic_year_id", Collections.singletonList(receiving_academic_year_id_processed));
        } else {
            LOG.fine("index-partner: receiving_academic_year_id is empty or invalid");
        }
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);
        LOG.fine("index-partner: Params: " + paramsMap);
        ClientResponse iiaResponse = restClient.sendRequest(clientRequest, OmobilityLasIndexResponse.class);

        GenericEntity<OmobilityLasIndexResponse> entity = null;
        try {
            OmobilityLasIndexResponse index = (OmobilityLasIndexResponse) iiaResponse.getResult();
            entity = new GenericEntity<OmobilityLasIndexResponse>(index) {
            };
        } catch (Exception e) {
            return javax.ws.rs.core.Response.serverError().entity(iiaResponse.getErrorMessage()).build();
        }

        return javax.ws.rs.core.Response.ok(entity).build();
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


    @GET
    @Path("get-partner")
    public Response getPartner(@QueryParam("sending_hei_id") String sending_hei_id, @QueryParam("omobility_id") String omobility_id, @QueryParam("type") String type) {
        LOG.fine("get-partner: Hei searched: " + sending_hei_id);

        Map<String, String> heiUrls = registryClient.getOmobilityLasHeiUrls(sending_hei_id);
        if (heiUrls == null || heiUrls.isEmpty()) {
            LOG.fine("get-partner: Hei not found: " + sending_hei_id);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, String> entry : heiUrls.entrySet()) {
            LOG.fine("get-partner: Hei URL: " + entry.getKey() + " -> " + entry.getValue());
        }
        String heiUrl = heiUrls.get("get-url");
        if (heiUrl == null || heiUrl.isEmpty()) {
            LOG.fine("get-partner: Hei URL not found for: " + sending_hei_id);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("get-partner: Hei URL found: " + heiUrl);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(sending_hei_id);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(heiUrl);
        Map<String, List<String>> paramsMap = new HashMap<>();
        if (sending_hei_id != null && !sending_hei_id.isEmpty()) {
            paramsMap.put("sending_hei_id", Collections.singletonList(sending_hei_id));
        } else {
            LOG.fine("get-partner: sending_hei_id is empty");
        }
        if (omobility_id != null && !omobility_id.isEmpty()) {
            paramsMap.put("omobility_id", Collections.singletonList(omobility_id));
        } else {
            LOG.fine("get-partner: receiving_hei_id is empty");
        }
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);
        LOG.fine("get-partner: Params: " + paramsMap);
        ClientResponse iiaResponse = restClient.sendRequest(clientRequest, OmobilityLasGetResponse.class);

        GenericEntity<OmobilityLasGetResponse> entity = null;
        try {
            OmobilityLasGetResponse index = (OmobilityLasGetResponse) iiaResponse.getResult();
            entity = new GenericEntity<OmobilityLasGetResponse>(index) {
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
    @Path("sync-partner")
    public Response syncPartner(@QueryParam("sending_hei_id") String sending_hei_id, @QueryParam("receiving_hei_id") String receiving_hei_id, @QueryParam("receiving_academic_year_id") String receiving_academic_year_id) {
        LOG.fine("sync-partner: Hei searched: " + sending_hei_id);

        Map<String, String> heiUrls = registryClient.getOmobilityLasHeiUrls(sending_hei_id);
        if (heiUrls == null || heiUrls.isEmpty()) {
            LOG.fine("sync-partner: Hei not found: " + sending_hei_id);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, String> entry : heiUrls.entrySet()) {
            LOG.fine("sync-partner: Hei URL: " + entry.getKey() + " -> " + entry.getValue());
        }
        String heiUrl = heiUrls.get("index-url");
        String getUrl = heiUrls.get("get-url");
        if (heiUrl == null || heiUrl.isEmpty()) {
            LOG.fine("sync-partner: Hei URL not found for: " + sending_hei_id);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("sync-partner: Hei URL found: " + heiUrl);

        String receiving_academic_year_id_processed = processAcademicYearId(receiving_academic_year_id);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(sending_hei_id);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(heiUrl);
        Map<String, List<String>> paramsMap = new HashMap<>();
        if (sending_hei_id != null && !sending_hei_id.isEmpty()) {
            paramsMap.put("sending_hei_id", Collections.singletonList(sending_hei_id));
        } else {
            LOG.fine("sync-partner: sending_hei_id is empty");
        }
        if (receiving_hei_id != null && !receiving_hei_id.isEmpty()) {
            paramsMap.put("receiving_hei_id", Collections.singletonList(receiving_hei_id));
        } else {
            LOG.fine("sync-partner: receiving_hei_id is empty");
        }
        if (receiving_academic_year_id_processed != null && !receiving_academic_year_id_processed.isEmpty()) {
            paramsMap.put("receiving_academic_year_id", Collections.singletonList(receiving_academic_year_id_processed));
        } else {
            LOG.fine("sync-partner: receiving_academic_year_id is empty or invalid");
        }
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);
        LOG.fine("sync-partner: Params: " + paramsMap);
        ClientResponse iiaResponse = restClient.sendRequest(clientRequest, OmobilityLasIndexResponse.class);

        GenericEntity<OmobilityLasIndexResponse> entity = null;
        try {
            OmobilityLasIndexResponse index = (OmobilityLasIndexResponse) iiaResponse.getResult();
            entity = new GenericEntity<OmobilityLasIndexResponse>(index) {
            };
        } catch (Exception e) {
            return javax.ws.rs.core.Response.serverError().entity(iiaResponse.getErrorMessage()).build();
        }

        SyncReturnDTO syncReturnDTO = new SyncReturnDTO();
        syncReturnDTO.setOmobilityIds(new ArrayList<>());

        entity.getEntity().getOmobilityId().forEach(id -> {
            LOG.fine("sync-partner: Processing omobility_id: " + id);
            OlearningAgreement olearningAgreement = learningAgreementEJB.findBySendingHeiIdAndOmobilityId(sending_hei_id, id);
            if (olearningAgreement == null) {
                LOG.fine("sync-partner: OlearningAgreement not found: " + id);
                ClientRequest clientRequestGet = new ClientRequest();
                clientRequestGet.setHeiId(sending_hei_id);
                clientRequestGet.setHttpsec(true);
                clientRequestGet.setMethod(HttpMethodEnum.GET);
                clientRequestGet.setUrl(getUrl);
                Map<String, List<String>> paramsMapGet = new HashMap<>();
                if (sending_hei_id != null && !sending_hei_id.isEmpty()) {
                    paramsMapGet.put("sending_hei_id", Collections.singletonList(sending_hei_id));
                }
                if (id != null && !id.isEmpty()) {
                    paramsMapGet.put("omobility_id", Collections.singletonList(id));
                }
                ParamsClass paramsGet = new ParamsClass();
                paramsGet.setUnknownFields(paramsMapGet);
                clientRequestGet.setParams(paramsGet);
                LOG.fine("sync-partner: get Params: " + paramsMapGet);
                ClientResponse getResponse = restClient.sendRequest(clientRequestGet, OmobilityLasGetResponse.class);
                try {
                    OmobilityLasGetResponse getIndex = (OmobilityLasGetResponse) getResponse.getResult();
                    if (getIndex.getLa() != null && !getIndex.getLa().isEmpty()) {
                        LearningAgreement learningAgreement = getIndex.getLa().get(0);
                        OlearningAgreement newOlearningAgreement = converter.convertToOlearningAgreement(learningAgreement, true, null);
                        String ourId = learningAgreementEJB.insert(newOlearningAgreement);
                        LOG.fine("sync-partner: OlearningAgreement inserted: " + id);

                        SyncReturnDTO.SyncReturnItemDTO syncReturnItemDTO = new SyncReturnDTO.SyncReturnItemDTO();
                        syncReturnItemDTO.setOmobilityId(id);
                        syncReturnItemDTO.setOurewpid(ourId);
                        syncReturnDTO.getOmobilityIds().add(syncReturnItemDTO);
                    } else {
                        LOG.fine("sync-partner: No LearningAgreement found in get response for omobility_id: " + id);
                    }
                } catch (Exception e) {
                    LOG.fine("sync-partner: Error processing get response for omobility_id " + id + ": " + e.getMessage());
                }
            } else {
                LOG.fine("sync-partner: OlearningAgreement found: " + id);
                SyncReturnDTO.SyncReturnItemDTO syncReturnItemDTO = new SyncReturnDTO.SyncReturnItemDTO();
                syncReturnItemDTO.setOmobilityId(id);
                syncReturnItemDTO.setOurewpid(olearningAgreement.getId());
                syncReturnDTO.getOmobilityIds().add(syncReturnItemDTO);
            }
        });

        return javax.ws.rs.core.Response.ok(syncReturnDTO).build();
    }

    @GET
    @Path("by-partner")
    public Response hello(@QueryParam("sending_hei_id") String sendingHeiId, @QueryParam("omobility_id") String id) {
        OmobilityLasGetResponse response = new OmobilityLasGetResponse();
        OlearningAgreement olearningAgreement = learningAgreementEJB.findBySendingHeiIdAndOmobilityId(sendingHeiId, id);
        if (olearningAgreement == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        response.getLa().add(converter.convertToLearningAgreements(olearningAgreement));
        return Response.ok(response).build();
    }

    @POST
    @Path("save/approve")
    @Consumes(MediaType.APPLICATION_XML)
    @Produces(MediaType.APPLICATION_XML)
    public Response saveApproval(@QueryParam("id") String id, OmobilityLasUpdateRequest omobilityLasUpdateRequest) {
        LOG.fine("SAVE APPROVE: start");
        LOG.fine("SAVE APPROVE: ownId: " + id);
        LOG.fine("SAVE APPROVE request: " + omobilityLasUpdateRequest.toString());

        learningAgreementEJB.approveChangesProposal(omobilityLasUpdateRequest, id);

        return Response.ok(omobilityLasUpdateRequest).build();
    }

    @POST
    @Path("send-cnr")
    public Response cnr(@QueryParam("sending_hei_id") String sendingHeiId, @QueryParam("omobility_id") String id) {

        LOG.fine("CNR: start");
        if (sendingHeiId == null || sendingHeiId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing sending_hei_id").build();
        }
        if (id == null || id.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Missing omobility_id").build();
        }

        Map<String, String> urls = registryClient.getOmobilityLaCnrHeiUrls(sendingHeiId);
        if (urls == null || urls.isEmpty()) {
            LOG.fine("CNR: No CNR URLs found for HEI " + sendingHeiId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        urls.forEach((key, value) -> LOG.fine("CNR: url: " + key + " -> " + value));

        for (Map.Entry<String, String> entry : urls.entrySet()) {
            ClientRequest clientRequest = new ClientRequest();
            clientRequest.setUrl(entry.getValue());
            clientRequest.setHeiId(sendingHeiId);
            clientRequest.setMethod(HttpMethodEnum.POST);
            clientRequest.setHttpsec(true);

            Map<String, List<String>> paramsMap = new HashMap<>();
            paramsMap.put("sending_hei_id", Collections.singletonList(sendingHeiId));
            paramsMap.put("omobility_id", Collections.singletonList(id));
            ParamsClass paramsClass = new ParamsClass();
            paramsClass.setUnknownFields(paramsMap);
            clientRequest.setParams(paramsClass);

            ClientResponse cnrResponse = restClient.sendRequest(clientRequest, Empty.class);
            LOG.fine("CNR: response: " + cnrResponse.getRawResponse());

            try {
                if (cnrResponse.getStatusCode() <= 599 && cnrResponse.getStatusCode() >= 400) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "ola-cnr", null, Integer.toString(cnrResponse.getStatusCode()), cnrResponse.getErrorMessage(), null);
                } else if (cnrResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "ola-cnr", null, Integer.toString(cnrResponse.getStatusCode()), cnrResponse.getErrorMessage(), "Error");
                }
            } catch (Exception e) {
                // swallow monitoring errors
            }
        }

        return Response.ok(id).build();
    }

}
