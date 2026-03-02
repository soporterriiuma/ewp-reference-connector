package eu.erasmuswithoutpaper.iia.boundary;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse;
import eu.erasmuswithoutpaper.common.control.*;
import eu.erasmuswithoutpaper.iia.approval.entity.IiaApproval;
import eu.erasmuswithoutpaper.iia.common.IiaTaskEnum;
import eu.erasmuswithoutpaper.iia.common.IiaTaskService;
import eu.erasmuswithoutpaper.iia.control.HashCalculationUtility;
import eu.erasmuswithoutpaper.iia.control.IiasEJB;
import eu.erasmuswithoutpaper.iia.dto.ApprovedHashesDto;
import eu.erasmuswithoutpaper.iia.entity.*;
import eu.erasmuswithoutpaper.iia.job.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.iias.approval.IiasApprovalResponse;
import eu.erasmuswithoutpaper.api.iias.endpoints.IiasGetResponse;
import eu.erasmuswithoutpaper.common.boundary.ClientRequest;
import eu.erasmuswithoutpaper.common.boundary.ClientResponse;
import eu.erasmuswithoutpaper.common.boundary.HttpMethodEnum;
import eu.erasmuswithoutpaper.common.boundary.ParamsClass;
import eu.erasmuswithoutpaper.iia.control.IiaConverter;
import eu.erasmuswithoutpaper.monitoring.SendMonitoringService;
import eu.erasmuswithoutpaper.organization.entity.Institution;
import eu.erasmuswithoutpaper.security.InternalAuthenticate;

@Path("iia")
public class GuiIiaResource {

    @EJB
    IiasEJB iiasEJB;

    @EJB
    JobRegistry jobs;
    @EJB
    HashRecalcJob job;
    @EJB
    GetDupeJob dupeJob;

    @Inject
    RegistryClient registryClient;

    @Inject
    RestClient restClient;

    @Inject
    IiaConverter iiaConverter;

    @Inject
    GlobalProperties properties;

    @Inject
    SendMonitoringService sendMonitoringService;

    private static final Logger logger = LoggerFactory.getLogger(GuiIiaResource.class);
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(AuxIiaThread.class.getCanonicalName());

    private static final java.nio.file.Path OUTPUT =
            Paths.get(System.getenv().getOrDefault("EXPORT_FILE", "/data/export.txt"));


    @GET
    @Path("index")
    @Produces(MediaType.APPLICATION_JSON)
    @InternalAuthenticate
    public Response index(@QueryParam("approved") Boolean approved, @QueryParam("heiId") String heiId) {
        IiasIndexResponse response = new IiasIndexResponse();
        response.getIiaId().addAll(iiasEJB.findIiaIds(approved, heiId));
        return Response.ok(response).build();
    }


    @GET
    @Path("get-range")
    @InternalAuthenticate
    public Response get(@QueryParam("init-date") Date initDate, @QueryParam("fin-date") Date finDate) {
        List<Iia> iiaList = iiasEJB.findByDateRange(initDate, finDate);
        List<IiasGetResponse.Iia> result = iiaConverter.convertToIias(iiasEJB.getHeiId(), iiaList);

        GenericEntity<List<IiasGetResponse.Iia>> entity = new GenericEntity<List<IiasGetResponse.Iia>>(result) {
        };

        return Response.ok(entity).build();
    }

    @GET
    @Path("get")
    @InternalAuthenticate
    public Response get(@QueryParam("iia_id") String iiaId, @QueryParam("partner_id") String partnerId, @QueryParam("type") String type) {
        if (iiaId != null) {
            Iia iia = iiasEJB.findById(iiaId);
            if (iia != null) {
                String heiId = iiasEJB.getHeiId();
                List<IiasGetResponse.Iia> iiaResponse = iiaConverter.convertToIias(heiId, Collections.singletonList(iia));
                if ("xml".equalsIgnoreCase(type)) {
                    IiasGetResponse response = new IiasGetResponse();
                    response.getIia().addAll(iiaResponse);
                    return Response.ok(response)
                            .type(MediaType.APPLICATION_XML)
                            .build();
                } else {
                    return Response.ok(iiaResponse)
                            .type(MediaType.APPLICATION_JSON)
                            .build();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } else if (partnerId != null) {
            List<Iia> iiaList = iiasEJB.findByPartnerId(partnerId);
            if (iiaList != null && !iiaList.isEmpty()) {
                List<IiasGetResponse.Iia> iiaResponseList = iiaConverter.convertToIias(partnerId, iiaList);
                GenericEntity<List<IiasGetResponse.Iia>> entity = new GenericEntity<List<IiasGetResponse.Iia>>(iiaResponseList) {
                };
                return Response.ok(entity).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }

    @GET
    @Path("get-approved")
    @InternalAuthenticate
    public Response getApproved(@QueryParam("iiaId") String iiaId, @QueryParam("type") String type) {
        if (iiaId == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        Iia iia = iiasEJB.findApprovedVersion(iiaId);

        if (iia == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String heiId = iiasEJB.getHeiId();
        List<IiasGetResponse.Iia> iiaResponse = iiaConverter.convertToIias(heiId, Collections.singletonList(iia));
        if ("xml".equalsIgnoreCase(type)) {
            IiasGetResponse response = new IiasGetResponse();
            response.getIia().addAll(iiaResponse);
            return Response.ok(response)
                    .type(MediaType.APPLICATION_XML)
                    .build();
        } else {
            return Response.ok(iiaResponse)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @GET
    @Path("get-all-approved")
    @Produces(MediaType.APPLICATION_JSON)
    public Response allApproved() {
        List<Iia> iiaList = iiasEJB.findApprovedVersions();

        if (iiaList == null || iiaList.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        String heiId = iiasEJB.getHeiId();
        List<IiasGetResponse.Iia> iiaResponse = iiaConverter.convertToIias(heiId, iiaList);
        return Response.ok(iiaResponse).build();
    }

    @GET
    @Path("get_other")
    @InternalAuthenticate
    public Response getOther(@QueryParam("iia_id") String iiaId, @QueryParam("hei_id") String heiId) {
        LOG.fine("AuxIiaThread_ADDEDIT: Empezando GET tras CNR");
        Map<String, String> map = registryClient.getIiaHeiUrls(heiId);
        if (map == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("AuxIiaThread_ADDEDIT: MAP ENCONTRADO");

        String url = map.get("get-url");
        if (url == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("AuxIiaThread_ADDEDIT: Url encontrada: " + url);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(heiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(url);

        Map<String, List<String>> paramsMap = new HashMap<>();
        paramsMap.put("iia_id", Arrays.asList(iiaId));
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);

        LOG.fine("AuxIiaThread_ADDEDIT: Parametros encontrados: ");

        paramsMap.forEach((key, value) -> {
            LOG.fine("\t\t\t\t" + key + ":" + value);
        });

        ClientResponse clientResponse = restClient.sendRequest(clientRequest, IiasGetResponse.class);

        Response response = Response.status(clientResponse.getStatusCode()).entity(clientResponse.getResult()).build();
        return response;
    }

    @GET
    @Path("get_all")
    @InternalAuthenticate
    // TODO: fix the default value
    public Response getAll(@QueryParam("hei_id") @DefaultValue("uma.es") String hei_id) {
        List<Iia> iiaList = iiasEJB.findAll();
        List<IiasGetResponse.Iia> result = iiaConverter.convertToIias(hei_id, iiaList);//It was required to use IiaConverter to avoid a recursive problem when the iia object was converted to json

        GenericEntity<List<IiasGetResponse.Iia>> entity = new GenericEntity<List<IiasGetResponse.Iia>>(result) {
        };

        return Response.ok(entity).build();
    }

    @GET
    @Path("get_heiid")
    @InternalAuthenticate
    public Response getHei(@QueryParam("hei_id") String heiId) {
        List<Iia> iiaList = iiasEJB.getByPartner(heiId);

        if (!iiaList.isEmpty()) {
            List<IiasGetResponse.Iia> iiasGetResponseList = iiaConverter.convertToIias(heiId, iiaList);

            GenericEntity<List<IiasGetResponse.Iia>> entity = new GenericEntity<List<IiasGetResponse.Iia>>(iiasGetResponseList) {
            };
            return Response.ok(entity).build();

        }

        return javax.ws.rs.core.Response.ok().build();
    }

    @GET
    @Path("get_heiid_list")
    @InternalAuthenticate
    public Response getHeiList(@QueryParam("hei_id") String heiId) {
        List<Iia> iiaList = iiasEJB.getByPartner(heiId);

        if (!iiaList.isEmpty()) {
            List<IiaForList> iiasGetResponseList = iiaConverter.toIiaForList(heiId, iiaList);

            GenericEntity<List<IiaForList>> entity = new GenericEntity<List<IiaForList>>(iiasGetResponseList) {
            };
            return Response.ok(entity).build();

        }

        return javax.ws.rs.core.Response.ok().build();
    }

    @GET
    @Path("mobility_types")
    @InternalAuthenticate
    public Response getMobilityTypes() {
        List<MobilityType> mobilityTypeList = iiasEJB.findMobilityTypes();
        GenericEntity<List<MobilityType>> entity = new GenericEntity<List<MobilityType>>(mobilityTypeList) {
        };

        return Response.ok(entity).build();
    }

    @GET
    @Path("mobility_unit_variants")
    @InternalAuthenticate
    public Response getMobilityNumberVariants() {
        String[] statuses = MobilityNumberVariants.names();
        GenericEntity<String[]> entity = new GenericEntity<String[]>(statuses) {
        };

        return Response.ok(entity).build();
    }

    @GET
    @Path("duration_unit_variants")
    @InternalAuthenticate
    public Response getDurationUnitVariants() {
        String[] statuses = DurationUnitVariants.names();
        GenericEntity<String[]> entity = new GenericEntity<String[]>(statuses) {
        };

        return Response.ok(entity).build();
    }

    @POST
    @Path("add")
    @InternalAuthenticate
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response add(IiasGetResponse.Iia iia) throws Exception {
        LOG.fine("ADD: Add start");
        Iia iiaInternal = new Iia();

        try {
            iiaConverter.convertToIia(iia, iiaInternal, iiasEJB.findAllInstitutions());
        } catch (Exception e) {
            LOG.fine("ADD: Error converting IIA: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity("Error converting IIA: " + e.getMessage()).build();
        }

        iiasEJB.insertIia(iiaInternal);
        //iiasEJB.updateHash(iiaInternal.getId());

        System.out.println("ADD: Created Iia Id:" + iiaInternal.getId());

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyPartner(iiaInternal);
        });

        LOG.fine("ADD: Notification send");

        IiaResponse response = new IiaResponse(iiaInternal.getId(), "dont forget recalculate hash!");

        return Response.ok(response).build();
    }

    @GET
    @Path("heis")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response iiaHeis() {
        List<HeiEntry> heis = registryClient.getIiaHeisWithUrls();

        GenericEntity<List<HeiEntry>> entity = new GenericEntity<List<HeiEntry>>(heis) {
        };
        return javax.ws.rs.core.Response.ok(entity).build();
    }

    @POST
    @Path("iias-index")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response iiasIndex(ClientRequest clientRequest) {
        ClientResponse iiaResponse = restClient.sendRequest(clientRequest, eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse.class);

        try {
            if (iiaResponse.getStatusCode() <= 599 && iiaResponse.getStatusCode() >= 400) {
                sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "index", Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), null);
            } else if (iiaResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "index", Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), "Error");
            }
        } catch (Exception e) {

        }

        GenericEntity<eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse> entity = null;
        try {
            eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse index = (eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse) iiaResponse.getResult();
            entity = new GenericEntity<eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse>(index) {
            };
        } catch (Exception e) {
            return javax.ws.rs.core.Response.serverError().entity(iiaResponse.getErrorMessage()).build();
        }

        return javax.ws.rs.core.Response.ok(entity).build();
    }

    @GET
    @Path("iias-index")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response iiasIndex(@QueryParam("heiId") String heiId,
                                               @QueryParam("receiving_academic_year_id") String receiving_academic_year_id,
                                               @QueryParam("modified_since") String modified_since) {
        LOG.fine("iias-index: Hei searched: " + heiId);

        Map<String, String> heiUrls = registryClient.getIiaHeiUrls(heiId);
        if (heiUrls == null || heiUrls.isEmpty()) {
            LOG.fine("iias-index: Hei not found: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, String> entry : heiUrls.entrySet()) {
            LOG.fine("iias-index: Hei URL: " + entry.getKey() + " -> " + entry.getValue());
        }
        String heiUrl = heiUrls.get("index-url");
        if (heiUrl == null || heiUrl.isEmpty()) {
            LOG.fine("iias-index: Hei URL not found for: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("iias-index: Hei URL found: " + heiUrl);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(heiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(heiUrl);
        Map<String, List<String>> paramsMap = new HashMap<>();
        if (receiving_academic_year_id != null && !receiving_academic_year_id.isEmpty()) {
            paramsMap.put("receiving_academic_year_id", Collections.singletonList(receiving_academic_year_id));
        } else {
            LOG.fine("iias-index: receiving_academic_year_id is empty");
        }
        if (modified_since != null && !modified_since.isEmpty()) {
            paramsMap.put("modified_since", Collections.singletonList(modified_since));
        } else {
            LOG.fine("iias-index: modified_since is empty");
        }
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);
        LOG.fine("iias-index: Params: " + paramsMap);
        ClientResponse iiaResponse = restClient.sendRequest(clientRequest, IiasIndexResponse.class);

        try {
            if (iiaResponse.getStatusCode() <= 599 && iiaResponse.getStatusCode() >= 400) {
                sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "index", Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), null);
            } else if (iiaResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "index", Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), "Error");
            }
        } catch (Exception e) {

        }

        GenericEntity<eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse> entity = null;
        try {
            eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse index = (eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse) iiaResponse.getResult();
            entity = new GenericEntity<eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse>(index) {
            };
        } catch (Exception e) {
            return javax.ws.rs.core.Response.serverError().entity(iiaResponse.getErrorMessage()).build();
        }

        return javax.ws.rs.core.Response.ok(entity).build();
    }

    @POST
    @Path("iias")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response iias(ClientRequest clientRequest) {
        ClientResponse iiaResponse = restClient.sendRequest(clientRequest, eu.erasmuswithoutpaper.api.iias.endpoints.IiasGetResponse.class);

        try {
            if (iiaResponse.getStatusCode() <= 599 && iiaResponse.getStatusCode() >= 400) {
                sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "get", Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), null);
            } else if (iiaResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "get", Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), "Error");
            }
        } catch (Exception e) {

        }

        return javax.ws.rs.core.Response.ok(iiaResponse).build();
    }

    @GET
    @Path("iias")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response iias(@QueryParam("iiaId") String iiaId, @QueryParam("type") String type, @QueryParam("heiId") String partnerHiid, @QueryParam("partnerId") String partnerIdParam) {
        Iia iia = null;
        if (iiaId != null && !iiaId.isEmpty()) {
            LOG.fine("iias: IIA searched: " + iiaId);
            iia = iiasEJB.findById(iiaId);
        } else if (partnerIdParam != null && !partnerIdParam.isEmpty() && partnerHiid != null && !partnerHiid.isEmpty()) {
            LOG.fine("iias: IIA searched by partnerId: " + partnerIdParam);
            List<Iia> iiaList = iiasEJB.getByPartnerId(partnerHiid, partnerIdParam);
            if (iiaList != null && !iiaList.isEmpty()) {
                iia = iiaList.get(0);
            }
            iiaId = (iia != null) ? iia.getId() : null;
        } else {
            LOG.fine("iias: Missing parameters to search IIA");
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).entity("Missing parameters to search IIA").build();
        }

        String localHeiId = iiasEJB.getHeiId();

        if (iia == null) {
            LOG.fine("iias: IIA not found: " + iiaId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        IiaPartner partnerSending = null;
        IiaPartner partnerReceiving = null;

        String heiId = null;
        String partnerId = null;

        for (CooperationCondition condition : iia.getCooperationConditions()) {
            partnerSending = condition.getSendingPartner();
            partnerReceiving = condition.getReceivingPartner();

            LOG.fine("iias: partnerSending: " + partnerSending.getInstitutionId());
            LOG.fine("iias: partnerReceiving: " + partnerReceiving.getInstitutionId());

            Map<String, String> urls = null;

            if (!localHeiId.equals(partnerSending.getInstitutionId())) {
                heiId = partnerSending.getInstitutionId();
                partnerId = partnerSending.getIiaId();
            } else if (!localHeiId.equals(partnerReceiving.getInstitutionId())) {
                heiId = partnerReceiving.getInstitutionId();
                partnerId = partnerReceiving.getIiaId();
            }
        }

        if (heiId == null || heiId.isEmpty() || partnerId == null || partnerId.isEmpty()) {
            LOG.fine("iias: Could not determine heiId or partnerId for IIA: " + iiaId);
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).entity("Could not determine heiId or partnerId for IIA: " + iiaId).build();
        }

        LOG.fine("iias: heiId: " + heiId + ", partnerId: " + partnerId);


        Map<String, String> heiUrls = registryClient.getIiaHeiUrls(heiId);
        if (heiUrls == null || heiUrls.isEmpty()) {
            LOG.fine("iias: Hei not found: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        for (Map.Entry<String, String> entry : heiUrls.entrySet()) {
            LOG.fine("iias: Hei URL: " + entry.getKey() + " -> " + entry.getValue());
        }
        String heiUrl = heiUrls.get("get-url");
        if (heiUrl == null || heiUrl.isEmpty()) {
            LOG.fine("iias: Hei URL not found for: " + heiId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("iias: Hei URL found: " + heiUrl);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(heiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(heiUrl);
        Map<String, List<String>> paramsMap = new HashMap<>();
        paramsMap.put("iia_id", Collections.singletonList(partnerId));
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);
        LOG.fine("iias: Params: " + paramsMap);
        ClientResponse clientResponse = restClient.sendRequest(clientRequest, IiasGetResponse.class);

        try {
            if (clientResponse.getStatusCode() <= 599 && clientResponse.getStatusCode() >= 400) {
                sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "get", Integer.toString(clientResponse.getStatusCode()), clientResponse.getErrorMessage(), null);
            } else if (clientResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "get", Integer.toString(clientResponse.getStatusCode()), clientResponse.getErrorMessage(), "Error");
            }
        } catch (Exception e) {

        }

        IiasGetResponse iiaResponse = (IiasGetResponse) clientResponse.getResult();

        if (iiaResponse == null || iiaResponse.getIia() == null || iiaResponse.getIia().isEmpty()) {
            LOG.fine("iias: IIA not found in the response: " + iiaId);
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }


        if ("xml".equalsIgnoreCase(type)) {
            return Response.ok(iiaResponse)
                    .type(MediaType.APPLICATION_XML)
                    .build();
        } else {
            return Response.ok(iiaResponse)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

    }

    @POST
    @Path("update")
    @InternalAuthenticate
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response update(IiasGetResponse.Iia iia) {
        LOG.fine("UPDATE: Start Update");
        Iia iiaInternal = new Iia();

        try {
            iiaConverter.convertToIia(iia, iiaInternal, iiasEJB.findAllInstitutions());
        } catch (Exception e) {
            LOG.fine("ADD: Error converting IIA: " + e.getMessage());
            return Response.status(Response.Status.BAD_REQUEST).entity("Error converting IIA: " + e.getMessage()).build();
        }

        //LOG.fine("UPDATE: Iia Code: " + iiaInternal.getCooperationConditions().stream().map(c -> c.getDuration().getNumber().toString()).collect(Collectors.joining(", ")));

        //Find the iia by code
        List<Iia> foundIias = iiasEJB.findByIiaCode(iiaInternal.getIiaCode());

        Iia foundIia = (foundIias != null && !foundIias.isEmpty()) ? foundIias.get(0) : null;

        //Check if the iia exists
        if (foundIia == null) {
            //Find the iia by id
            foundIias = iiasEJB.findByIdList(iiaInternal.getId());

            foundIia = (foundIias != null && !foundIias.isEmpty()) ? foundIias.get(0) : null;

            if (foundIia == null) {
                return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
            }
        }

        if (iiaInternal.getIiaCode() == null || iiaInternal.getIiaCode().isEmpty()) {
            System.err.println("Update Algoria: Mising iiaCode");
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        if (iiaInternal.getCooperationConditions().size() == 0) {
            System.err.println("Update Algoria: Mising Cooperation Conditions");
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();

        } else if (!validateConditions(iiaInternal.getCooperationConditions())) {
            System.err.println("Update Algoria: Invalids Cooperation Conditions");
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }
        LOG.fine("OLODOLD Hash: " + foundIia.getConditionsHash());
        //String oldHash = foundIia.getConditionsHash();

        String newHash = iiasEJB.updateIia(iiaInternal, foundIia, foundIia.getHashPartner());
        LOG.fine("UPDATE: Iia Updated");
        /*String newHash = iiasEJB.updateHash(foundIia.getId());

        LOG.fine("OLD HASH: " + oldHash);
        LOG.fine("NEW HASH: " + newHash);*/
        //if (!oldHash.equals(newHash)) {
        iiasEJB.deleteAssociatedIiaApprovals(foundIia.getId());
        LOG.fine("UPDATE: Iia Approvals deleted");
        //}

        //Notify the partner about the modification using the API GUI IIA CNR
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyPartner(iiaInternal);
        });

        IiaResponse response = new IiaResponse(foundIia.getId(), newHash);
        return javax.ws.rs.core.Response.ok(response).build();
    }

    @POST
    @Path("sendCnr")
    @InternalAuthenticate
    public void resendCnr(@QueryParam("iiaId") String iiaId) {
        Iia iia = iiasEJB.findById(iiaId);
        if (iia == null) {
            return;
        }

        notifyPartner(iia);
    }

    @GET
    @Path("computeHash")
    @InternalAuthenticate
    public javax.ws.rs.core.Response computeHash(@QueryParam("iiaId") String iiaId) {
        Iia iia = iiasEJB.findById(iiaId);
        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        String hash = "";
        try {
            hash = HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(iiasEJB.getHeiId(), Collections.singletonList(iia)).get(0));
        } catch (Exception e) {
            return javax.ws.rs.core.Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        IiaResponse response = new IiaResponse(iiaId, hash);
        return javax.ws.rs.core.Response.ok(response).build();
    }

    @GET
    @Path("computeHashApproved")
    @InternalAuthenticate
    public javax.ws.rs.core.Response computeHashApproved(@QueryParam("iiaId") String iiaId) {
        Iia iia = iiasEJB.findApprovedVersion(iiaId);
        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        String hash = "";
        try {
            hash = HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(iiasEJB.getHeiId(), Collections.singletonList(iia)).get(0));
        } catch (Exception e) {
            return javax.ws.rs.core.Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        IiaResponse response = new IiaResponse(iiaId, hash);
        return javax.ws.rs.core.Response.ok(response).build();
    }

    @GET
    @Path("hash")
    @InternalAuthenticate
    public javax.ws.rs.core.Response reCalcHash(@QueryParam("iiaId") String iiaId) {
        Iia iia = iiasEJB.findById(iiaId);
        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        if (iia.getOriginal() != null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).entity("Cannot recalculate hash for approved version").build();
        }

        try {
            iia.setConditionsHash(HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(iiasEJB.getHeiId(), Collections.singletonList(iia)).get(0)));
            iiasEJB.update(iia);
        } catch (Exception e) {
            return javax.ws.rs.core.Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyPartner(iia);
        });

        IiaResponse response = new IiaResponse(iiaId, iia.getConditionsHash());
        return javax.ws.rs.core.Response.ok(response).build();
    }

    @GET
    @Path("hashApproved")
    @InternalAuthenticate
    public javax.ws.rs.core.Response reCalcHashApproved(@QueryParam("iiaId") String iiaId) {
        Iia iia = iiasEJB.findApprovedVersion(iiaId);
        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        try {
            iia.setConditionsHash(HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(iiasEJB.getHeiId(), Collections.singletonList(iia)).get(0)));
            iiasEJB.update(iia);
        } catch (Exception e) {
            return javax.ws.rs.core.Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }

        IiaResponse response = new IiaResponse(iiaId, iia.getConditionsHash());
        return javax.ws.rs.core.Response.ok(response).build();
    }

    private List<ClientResponse> notifyPartner(Iia iia) {
        LOG.fine("NOTIFY: Send notification");

        List<ClientResponse> partnersResponseList = new ArrayList<>();

        //Getting agreement partners
        IiaPartner partnerSending = null;
        IiaPartner partnerReceiving = null;

        Set<NotifyAux> cnrUrls = new HashSet<>();

        List<Institution> institutions = iiasEJB.findAllInstitutions();
        for (CooperationCondition condition : iia.getCooperationConditions()) {
            partnerSending = condition.getSendingPartner();
            partnerReceiving = condition.getReceivingPartner();

            LOG.fine("NOTIFY: partnerSending: " + partnerSending.getInstitutionId());
            LOG.fine("NOTIFY: partnerReceiving: " + partnerReceiving.getInstitutionId());

            Map<String, String> urls = null;
            for (Institution institution : institutions) {

                if (!institution.getInstitutionId().equals(partnerSending.getInstitutionId())) {

                    //Get the url for notify the institute not supported by our EWP
                    urls = registryClient.getIiaCnrHeiUrls(partnerSending.getInstitutionId());
                    LOG.fine("NOTIFY: urls: " + urls);

                    if (urls != null) {
                        for (Map.Entry<String, String> entry : urls.entrySet()) {
                            cnrUrls.add(new NotifyAux(partnerSending.getInstitutionId(), entry.getValue()));
                        }
                    }

                } else if (!institution.getInstitutionId().equals(partnerReceiving.getInstitutionId())) {

                    //Get the url for notify the institute not supported by our EWP
                    urls = registryClient.getIiaCnrHeiUrls(partnerReceiving.getInstitutionId());
                    LOG.fine("NOTIFY: urls: " + urls);

                    if (urls != null) {
                        for (Map.Entry<String, String> entry : urls.entrySet()) {
                            cnrUrls.add(new NotifyAux(partnerReceiving.getInstitutionId(), entry.getValue()));
                        }

                    }
                }

            }
        }

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
            paramsMap.put("iia_id", Collections.singletonList(iia.getId()));
            ParamsClass paramsClass = new ParamsClass();
            paramsClass.setUnknownFields(paramsMap);
            clientRequest.setParams(paramsClass);

            ClientResponse iiaResponse = restClient.sendRequest(clientRequest, Empty.class);

            try {
                if (iiaResponse.getStatusCode() <= 599 && iiaResponse.getStatusCode() >= 400) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iia-cnr", null, Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), null);
                } else if (iiaResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iia-cnr", null, Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), "Error");
                }
            } catch (Exception e) {

            }

            partnersResponseList.add(iiaResponse);
        });

        return partnersResponseList;

    }

    private List<ClientResponse> notifyPartnerApproval(Iia iia) {
        LOG.fine("NOTIFY: Send notification");

        List<ClientResponse> partnersResponseList = new ArrayList<>();

        //Getting agreement partners
        IiaPartner partnerSending = null;
        IiaPartner partnerReceiving = null;

        Set<NotifyAux> cnrUrls = new HashSet<>();

        List<Institution> institutions = iiasEJB.findAllInstitutions();
        for (CooperationCondition condition : iia.getCooperationConditions()) {
            partnerSending = condition.getSendingPartner();
            partnerReceiving = condition.getReceivingPartner();

            LOG.fine("NOTIFY: partnerSending: " + partnerSending.getInstitutionId());
            LOG.fine("NOTIFY: partnerReceiving: " + partnerReceiving.getInstitutionId());

            Map<String, String> urls = null;
            for (Institution institution : institutions) {

                if (!institution.getInstitutionId().equals(partnerSending.getInstitutionId())) {

                    //Get the url for notify the institute not supported by our EWP
                    urls = registryClient.getIiaApprovalCnrHeiUrls(partnerSending.getInstitutionId());

                    LOG.fine("NOTIFY: urls: " + urls);

                    if (urls != null) {
                        for (Map.Entry<String, String> entry : urls.entrySet()) {
                            cnrUrls.add(new NotifyAux(partnerSending.getInstitutionId(), entry.getValue(), partnerSending.getIiaId()));
                        }
                    }

                } else if (!institution.getInstitutionId().equals(partnerReceiving.getInstitutionId())) {

                    //Get the url for notify the institute not supported by our EWP
                    urls = registryClient.getIiaApprovalCnrHeiUrls(partnerReceiving.getInstitutionId());

                    LOG.fine("NOTIFY: urls: " + urls);

                    if (urls != null) {
                        for (Map.Entry<String, String> entry : urls.entrySet()) {
                            cnrUrls.add(new NotifyAux(partnerReceiving.getInstitutionId(), entry.getValue(), partnerReceiving.getIiaId()));
                        }

                    }
                }

            }
        }

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
            paramsMap.put("iia_id", Collections.singletonList(url.getIiaId()));
            ParamsClass paramsClass = new ParamsClass();
            paramsClass.setUnknownFields(paramsMap);
            clientRequest.setParams(paramsClass);

            ClientResponse iiaResponse = restClient.sendRequest(clientRequest, Empty.class);

            try {
                if (iiaResponse.getStatusCode() <= 599 && iiaResponse.getStatusCode() >= 400) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iia-cnr", null, Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), null);
                } else if (iiaResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iia-cnr", null, Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), "Error");
                }
            } catch (Exception e) {

            }

            partnersResponseList.add(iiaResponse);
        });

        return partnersResponseList;

    }


    @POST
    @Path("iias-approve")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response iiasApprove(@FormParam("hei_id") String heiId, @FormParam("iia_id") String
            iiaId) {

        String localHeiId = iiasEJB.getHeiId();
        //seek the iia by code and by the ouid of the sending institution
        List<Iia> foundIia = iiasEJB.findByIdListApp(iiaId);

        Predicate<Iia> condition = new Predicate<Iia>() {
            @Override
            public boolean test(Iia iia) {
                List<CooperationCondition> cooperationConditions = iia.getCooperationConditions();

                List<CooperationCondition> filtered = cooperationConditions.stream().filter(c -> localHeiId.equals(c.getSendingPartner().getInstitutionId())).collect(Collectors.toList());
                return !filtered.isEmpty();
            }
        };

        foundIia.stream().filter(condition).collect(Collectors.toList());

        if (foundIia.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        //get the first one found
        Iia theIia = foundIia.get(0);

        String partnerId = "";
        for (CooperationCondition c : theIia.getCooperationConditions()) {
            if (c.getSendingPartner().getInstitutionId().equals(localHeiId)) {
                partnerId = c.getReceivingPartner().getIiaId();
            } else if (c.getReceivingPartner().getInstitutionId().equals(localHeiId)) {
                partnerId = c.getSendingPartner().getIiaId();
            }
        }

        LOG.fine("iias-approve: IIA found, retrieving current partner hash");
        IiasGetResponse.Iia remoteIia = sendGet(heiId, partnerId);
        if (remoteIia == null || remoteIia.getIiaHash() == null || remoteIia.getIiaHash().isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_GATEWAY)
                    .entity("No se pudo obtener el hash actual del partner").build();
        }

        LOG.fine("Iia found: " + theIia.getId());

        IiaApproval approval = new IiaApproval();
        approval.setIiaCode(foundIia.get(0).getIiaCode());
        approval.setIia(theIia);
        approval.setHeiId(localHeiId);
        approval.setConditionsHash(theIia.getConditionsHash());
        approval.setConditionsHashPartner(remoteIia.getIiaHash());

        iiasEJB.insertIiaApproval(approval);

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyPartnerApproval(theIia);
        });

        return javax.ws.rs.core.Response.ok().build();
        /*
        //Getting agreement partners
        IiaPartner partnerReceiving = null;

        for (CooperationCondition cooCondition : theIia.getCooperationConditions()) {
            partnerReceiving = cooCondition.getReceivingPartner();
        }

        //Verify if the agreement is approved by the other institution.
        Map<String, String> urlsGet = registryClient.getIiaApprovalHeiUrls(heiId);
        List<String> urlGetValues = new ArrayList<String>(urlsGet.values());

        ClientRequest clientRequestGetIia = new ClientRequest();
        clientRequestGetIia.setUrl(urlGetValues.get(0));//get the first and only one url
        clientRequestGetIia.setHeiId(partnerReceiving.getInstitutionId());
        clientRequestGetIia.setMethod(HttpMethodEnum.GET);
        clientRequestGetIia.setHttpsec(true);

        List<String> iiaIds = new ArrayList<>();
        iiaIds.add(theIia.getId());

        Map<String, List<String>> params = new HashMap<>();
        params.put("approving_hei_id", Arrays.asList(partnerReceiving.getInstitutionId()));
        params.put("owner_hei_id", Arrays.asList(heiId));
        params.put("iia_id", iiaIds);

        ParamsClass pc = new ParamsClass();
        pc.setUnknownFields(params);

        clientRequestGetIia.setParams(pc);

        ClientResponse iiaApprovalResponse = restClient.sendRequest(clientRequestGetIia, eu.erasmuswithoutpaper.api.iias.approval.IiasApprovalResponse.class);

        try {
            if (iiaApprovalResponse.getStatusCode() <= 599 && iiaApprovalResponse.getStatusCode() >= 400) {
                sendMonitoringService.sendMonitoring(clientRequestGetIia.getHeiId(), "iias-approval", null, Integer.toString(iiaApprovalResponse.getStatusCode()), iiaApprovalResponse.getErrorMessage(), null);
            } else if (iiaApprovalResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                sendMonitoringService.sendMonitoring(clientRequestGetIia.getHeiId(), "iias-approval", null, Integer.toString(iiaApprovalResponse.getStatusCode()), iiaApprovalResponse.getErrorMessage(), "Error");
            }
        } catch (Exception e) {

        }

        eu.erasmuswithoutpaper.api.iias.approval.IiasApprovalResponse response = (IiasApprovalResponse) iiaApprovalResponse.getResult();

        Approval approval = response.getApproval().stream()
                .filter(app -> theIia.getIiaCode().equals(app.getIiaId()))
                .findAny()
                .orElse(null);

        if (approval != null) {
            theIia.setInEfect(true);//it was approved
            //em.persist(theIia);
        }

        //Get the url for notify the institute
        Map<String, String> urls = registryClient.getIiaApprovalCnrHeiUrls(heiId);
        List<String> urlValues = new ArrayList<String>(urls.values());

        //Notify the other institution about the approval
        ClientRequest clientRequestNotifyApproval = new ClientRequest();
        clientRequestNotifyApproval.setUrl(urlValues.get(0));//get the first and only one url
        clientRequestNotifyApproval.setHeiId(partnerReceiving.getInstitutionId());
        clientRequestNotifyApproval.setMethod(HttpMethodEnum.POST);
        clientRequestNotifyApproval.setHttpsec(true);

        Map<String, List<String>> paramsCnr = new HashMap<>();
        paramsCnr.put("approving_hei_id", Arrays.asList(partnerReceiving.getInstitutionId()));
        paramsCnr.put("owner_hei_id", Arrays.asList(heiId));
        paramsCnr.put("iia_id", iiaIds);

        ParamsClass pc2 = new ParamsClass();
        pc2.setUnknownFields(paramsCnr);

        clientRequestNotifyApproval.setParams(pc2);

        ClientResponse iiaResponse = restClient.sendRequest(clientRequestNotifyApproval, Object.class);

        try {
            if (iiaResponse.getStatusCode() <= 599 && iiaResponse.getStatusCode() >= 400) {
                sendMonitoringService.sendMonitoring(clientRequestNotifyApproval.getHeiId(), "iia-approval-cnr", null, Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), null);
            } else if (iiaResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                sendMonitoringService.sendMonitoring(clientRequestNotifyApproval.getHeiId(), "iia-approval-cnr", null, Integer.toString(iiaResponse.getStatusCode()), iiaResponse.getErrorMessage(), "Error");
            }
        } catch (Exception e) {

        }

        return javax.ws.rs.core.Response.ok(iiaResponse).build();*/
    }

    @DELETE
    @Path("delete")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response delete(@FormParam("iia_id") String iiaId) {
        if (iiaId == null || iiaId.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Iia iia = iiasEJB.findById(iiaId);

        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        if (iiasEJB.findApprovedVersion(iiaId) != null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).entity("The IIA is approved").build();
        }

        iiasEJB.deleteIia(iia);

        // Notify the partner about the deletion
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyPartner(iia);
        });

        return javax.ws.rs.core.Response.ok().build();
    }

    @DELETE
    @Path("delete/all")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response deleteAll() {
        iiasEJB.deleteAllIias();

        return javax.ws.rs.core.Response.ok().build();
    }

    private boolean validateConditions(List<CooperationCondition> conditions) {

        Predicate<CooperationCondition> condition = new Predicate<CooperationCondition>() {
            @Override
            public boolean test(CooperationCondition c) {
                if (c.getSendingPartner() == null || c.getReceivingPartner() == null) {
                    return true;
                } else if (c.getSendingPartner().getInstitutionId() == null || c.getSendingPartner().getInstitutionId().isEmpty()) {
                    return true;
                } else if (c.getReceivingPartner().getInstitutionId() == null || c.getReceivingPartner().getInstitutionId().isEmpty()) {
                    return true;
                }
                return false;
            }

        };

        List<CooperationCondition> wrongConditions = conditions.stream().filter(condition).collect(Collectors.toList());
        return wrongConditions.isEmpty();
    }

    @GET
    @Path("clone")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response clone(@QueryParam("iia_id") String iiaId) {
        if (iiaId == null || iiaId.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Iia iia = iiasEJB.findById(iiaId);

        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        String heiId = iiasEJB.getHeiId();

        List<IiasGetResponse.Iia> iiaResponse = iiaConverter.convertToIias(heiId, Collections.singletonList(iia));
        Iia newIia = new Iia();
        iiaConverter.convertToIia(iiaResponse.get(0), newIia, iiasEJB.findAllInstitutions());

        Iia clonedIia = iiasEJB.saveApprovedVersion(newIia, iia.getModifyDate(), iia.getHashPartner());

        return javax.ws.rs.core.Response.ok(clonedIia).build();
    }

    @GET
    @Path("revert")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response revert(@QueryParam("iia_id") String iiaId) {
        if (iiaId == null || iiaId.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Iia iia = iiasEJB.findById(iiaId);

        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        Iia clonedIia = iiasEJB.findApprovedVersion(iiaId);

        if (clonedIia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        iiasEJB.revertIia(iia.getId(), clonedIia.getId());
        iiasEJB.updateHash(iia.getId());

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyPartner(iia);
        });

        return javax.ws.rs.core.Response.ok().build();
    }

    @GET
    @Path("terminate")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response terminate(@QueryParam("iia_id") String iiaId) {
        if (iiaId == null || iiaId.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Iia iia = iiasEJB.findById(iiaId);

        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        if (iiasEJB.findApprovedVersion(iiaId) == null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).entity("The IIA is not approved").build();
        }

        LOG.fine("GuiIiaRecource: Terminating IIA: " + iia.getId());

        LOG.fine("GuiIiaRecource: Before hash: " + iia.getConditionsHash());

        iiasEJB.terminateIia(iia.getId());
        iiasEJB.updateHash(iia.getId());

        // Notify the partner about the deletion
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            notifyPartner(iia);
        });

        return javax.ws.rs.core.Response.ok().build();
    }

    @GET
    @Path("get-partner")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response getPartner(@QueryParam("iia_id") String iiaId, @QueryParam("partner_iia_id") String partnerIiaId, @QueryParam("partner_hei_id") String partnerHeiId) {
        if (iiaId != null && !iiaId.isEmpty()) {

            String localHeiId = iiasEJB.getHeiId();
            Iia iia = iiasEJB.findById(iiaId);
            if (iia == null) {
                return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
            }
            for (CooperationCondition c : iia.getCooperationConditions()) {
                LOG.fine("GuiIiaRecource: Sending Partner: " + c.getSendingPartner().getInstitutionId());
                LOG.fine("GuiIiaRecource: Receiving Partner: " + c.getReceivingPartner().getInstitutionId());
                if (c.getSendingPartner().getInstitutionId().equals(localHeiId)) {
                    partnerHeiId = c.getReceivingPartner().getInstitutionId();
                } else if (c.getReceivingPartner().getInstitutionId().equals(localHeiId)) {
                    partnerHeiId = c.getSendingPartner().getInstitutionId();
                }
            }
        } else {
            iiaId = partnerIiaId;
        }

        Map<String, String> map = registryClient.getIiaHeiUrls(partnerHeiId);
        if (map == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        String url = map.get("get-url");
        if (url == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(partnerHeiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(url);

        Map<String, List<String>> paramsMap = new HashMap<>();
        paramsMap.put("iia_id", Arrays.asList(iiaId));
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);

        ClientResponse clientResponse = restClient.sendRequest(clientRequest, IiasGetResponse.class);

        IiasGetResponse responseEnity = (IiasGetResponse) clientResponse.getResult();

        if (responseEnity == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        List<IiasGetResponse.Iia> iias = responseEnity.getIia();

        return javax.ws.rs.core.Response.ok(iias).build();
    }

    @GET
    @Path("get-approval")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response getApproval(@QueryParam("iia_id") String iiaId) {
        if (iiaId == null || iiaId.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }
        String localHeiId = iiasEJB.getHeiId();
        String partnerHeiId = "";
        Iia iia = iiasEJB.findById(iiaId);
        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }
        for (CooperationCondition c : iia.getCooperationConditions()) {
            LOG.fine("GuiIiaRecource: Sending Partner: " + c.getSendingPartner().getInstitutionId());
            LOG.fine("GuiIiaRecource: Receiving Partner: " + c.getReceivingPartner().getInstitutionId());
            if (c.getSendingPartner().getInstitutionId().equals(localHeiId)) {
                partnerHeiId = c.getReceivingPartner().getInstitutionId();
            } else if (c.getReceivingPartner().getInstitutionId().equals(localHeiId)) {
                partnerHeiId = c.getSendingPartner().getInstitutionId();
            }
        }

        Map<String, String> map = registryClient.getIiaApprovalHeiUrls(partnerHeiId);
        if (map == null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        String url = map.get("url");
        if (url == null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(partnerHeiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(url);

        Map<String, List<String>> paramsMap = new HashMap<>();
        paramsMap.put("iia_id", Arrays.asList(iiaId));
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);

        ClientResponse clientResponse = restClient.sendRequest(clientRequest, IiasApprovalResponse.class);
        IiasApprovalResponse responseEnity = (IiasApprovalResponse) clientResponse.getResult();

        if (responseEnity == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        if (responseEnity.getApproval() == null || responseEnity.getApproval().isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        IiasApprovalResponse.Approval approval = responseEnity.getApproval().get(0);


        return javax.ws.rs.core.Response.ok(approval).build();
    }

    @POST
    @Path("sendAlgoria")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response sendAlgoria(@FormParam("type") String type, @QueryParam("iiaId") String iiaId) throws JsonProcessingException {
        if (type == null || type.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }
        if (iiaId == null || iiaId.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Iia iia = iiasEJB.findById(iiaId);
        String heiId = iiasEJB.getHeiId();
        IiasGetResponse.Iia iiaResponse = iiaConverter.convertToIias(heiId, Collections.singletonList(iia)).get(0);

        AtomicReference<String> partnerHeiId = new AtomicReference<>("");
        iiaResponse.getPartner().forEach(i -> {
            if (!i.getHeiId().equals(heiId)) {
                partnerHeiId.set(i.getHeiId());
            }
        });

        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        iiaResponse.setIiaHash(null);
        String json = ow.writeValueAsString(iiaResponse);

        switch (type) {
            case "CREATED":
                execNotificationToAlgoria(IiaTaskEnum.CREATED, iiaId, partnerHeiId.get(), json);
                break;
            case "UPDATED":
                execNotificationToAlgoria(IiaTaskEnum.UPDATED, iiaId, partnerHeiId.get(), json);
                break;
            case "APPROVED":
                execNotificationToAlgoria(IiaTaskEnum.APPROVED, iiaId, partnerHeiId.get(), "Aproved");
                break;
            case "MODIFY":
                execNotificationToAlgoria(IiaTaskEnum.MODIFY, iiaId, partnerHeiId.get(), json);
                break;
            case "DELETED":
                execNotificationToAlgoria(IiaTaskEnum.DELETED, iiaId, partnerHeiId.get(), "Deleted");
                break;
            case "REVERTED":
                execNotificationToAlgoria(IiaTaskEnum.REVERTED, iiaId, partnerHeiId.get(), json);
                break;
            case "TERMINATED":
                execNotificationToAlgoria(IiaTaskEnum.TERMINATED, iiaId, partnerHeiId.get(), "Terminated");
                break;
        }

        return javax.ws.rs.core.Response.ok().build();
    }

    @GET
    @Path("urls")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response getUrls(@QueryParam("heiId") String heiId, @QueryParam("type") String type) {

        Map<String, String> map = registryClient.getIiaHeiUrls(heiId);
        if (map == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        LOG.fine("URLS: MAP ENCONTRADO");
        String url = map.get(type);
        if (url == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        return javax.ws.rs.core.Response.ok(url).build();
    }

    @GET
    @Path("XML")
    @Consumes("application/xml")
    @Produces("application/xml")
    public Response getXML(@QueryParam("id") String id) {
        LOG.fine("XML: start");

        Iia iia = iiasEJB.findById(id);
        IiasGetResponse response = new IiasGetResponse();
        response.getIia().add(iiaConverter.convertToIias(iiasEJB.getHeiId(), Collections.singletonList(iia)).get(0));

        return Response.ok(response).build();
    }

    private void execNotificationToAlgoriaApprove(String iiaApprovalId, String approvingHeiId) {

        IiaTaskService.globalProperties = properties;
        Callable<String> callableTask = IiaTaskService.createTask(iiaApprovalId, IiaTaskEnum.APPROVED, approvingHeiId);

        //Put the task in the queue
        IiaTaskService.addTask(callableTask);
    }

    private void execNotificationToAlgoria(IiaTaskEnum type, String iiaId, String heiId, String description) {

        IiaTaskService.globalProperties = properties;
        Callable<String> callableTask = IiaTaskService.createTask(iiaId, type, heiId, description);

        //Put the task in the queue
        IiaTaskService.addTask(callableTask);
    }

    private IiasGetResponse.Iia sendGet(String heiId, String iiaId) {
        LOG.fine("GuiIiaRecource: Empezando GET tras CNR");
        Map<String, String> map = registryClient.getIiaHeiUrls(heiId);
        if (map == null) {
            return null;
        }

        LOG.fine("GuiIiaRecource: MAP ENCONTRADO");

        String url = map.get("get-url");
        if (url == null) {
            return null;
        }

        LOG.fine("GuiIiaRecource: Url encontrada: " + url);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(heiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(url);

        Map<String, List<String>> paramsMap = new HashMap<>();
        paramsMap.put("iia_id", Arrays.asList(iiaId));
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);

        LOG.fine("GuiIiaRecource: Parametros encontrados: ");

        paramsMap.forEach((key, value) -> {
            LOG.fine("\t\t\t\t" + key + ":" + value);
        });

        ClientResponse clientResponse = restClient.sendRequest(clientRequest, IiasGetResponse.class);

        LOG.fine("GuiIiaRecource: Respuesta del cliente " + clientResponse.getStatusCode());

        try {
            if (clientResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                if (clientResponse.getStatusCode() <= 599 && clientResponse.getStatusCode() >= 400) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "get", Integer.toString(clientResponse.getStatusCode()), clientResponse.getErrorMessage(), null);
                } else if (clientResponse.getStatusCode() != Response.Status.OK.getStatusCode()) {
                    sendMonitoringService.sendMonitoring(clientRequest.getHeiId(), "iias", "get", Integer.toString(clientResponse.getStatusCode()), clientResponse.getErrorMessage(), "Error");
                }
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        LOG.fine("GuiIiaRecource: Respuesta raw: " + clientResponse.getRawResponse());

        IiasGetResponse responseEnity = (IiasGetResponse) clientResponse.getResult();

        return responseEnity.getIia().get(0);
    }


    @GET
    @Path("get-partner-list")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response getPartnerIds() {
        List<Iia> iias = iiasEJB.findAll();
        iias = iias.stream()
                .filter(iia -> iia.getOriginal() == null)
                .collect(Collectors.toList());
        String heiId = iiasEJB.getHeiId();

        Map<String, Map<String, List<String>>> partnerIds = getDupeMap(iias, heiId);

        if (partnerIds.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        return javax.ws.rs.core.Response.ok(partnerIds).build();
    }

    private static Map<String, Map<String, List<String>>> getDupeMap(List<Iia> iias, String heiId) {
        Map<String, Map<String, List<String>>> partnerIds = new HashMap<>();
        for (Iia iia : iias) {
            IiaPartner partner = null;
            if (iia.getCooperationConditions() != null) {
                for (CooperationCondition condition : iia.getCooperationConditions()) {
                    if (condition.getSendingPartner() != null && condition.getSendingPartner().getInstitutionId() != null
                            && !condition.getSendingPartner().getInstitutionId().equals(heiId)) {
                        partner = condition.getSendingPartner();
                    }
                    if (condition.getReceivingPartner() != null && condition.getReceivingPartner().getInstitutionId() != null
                            && !condition.getReceivingPartner().getInstitutionId().equals(heiId)) {
                        partner = condition.getReceivingPartner();
                    }
                }
            }

            if (partner != null) {
                if (!partnerIds.containsKey(partner.getInstitutionId())) {
                    Map<String, List<String>> iiaList = new HashMap<>();
                    List<String> iiaIds = new ArrayList<>();
                    iiaIds.add(iia.getId());
                    iiaList.put(partner.getIiaId(), iiaIds);
                    partnerIds.put(partner.getInstitutionId(), iiaList);
                } else {
                    Map<String, List<String>> iiaList = partnerIds.get(partner.getInstitutionId());
                    if (iiaList.containsKey(partner.getIiaId())) {
                        iiaList.get(partner.getIiaId()).add(iia.getId());
                    } else {
                        List<String> iiaIds = new ArrayList<>();
                        iiaIds.add(iia.getId());
                        iiaList.put(partner.getIiaId(), iiaIds);
                    }

                }
            }

        }
        return partnerIds;
    }

    @POST
    @Path("get-partner-delete")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response getToDelete(List<String> iiaIds) {
        if (iiaIds == null || iiaIds.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        logger.info("GuiIiaResource: getToDelete: " + iiaIds);

        List<Iia> iias = iiasEJB.findAll();
        iias = iias.stream()
                .filter(iia -> iia.getOriginal() == null)
                .collect(Collectors.toList());

        List<String> toDelete = new ArrayList<>();
        for (Iia iia : iias) {
            if (!iiaIds.contains(iia.getId())) {
                toDelete.add(iia.getId());
            }
        }

        if (toDelete.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        List<String> response = new ArrayList<>();

        for (String iiaId : toDelete) {
            if (iiasEJB.isApproved(iiaId)) {
                response.add("The IIA with ID: " + iiaId + " is approved and cannot be deleted.");
            } else {
                //delete(iiaId);
                response.add("The IIA with ID: " + iiaId + " can be deleted.");
            }
        }
        return javax.ws.rs.core.Response.ok(response).build();
    }

    @GET
    @Path("get-approvals")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response getApprovals(@QueryParam("iiaId") String iiaId) {
        if (iiaId == null || iiaId.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Iia iia = iiasEJB.findById(iiaId);
        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        List<IiaApproval> approvals = iiasEJB.findIiaApproval(iiaId);

        Set<Map<String, String>> approvedHeiIds = approvals.stream()
                .map(iiaApproval -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("heiId", iiaApproval.getHeiId());
                    map.put("conditionsHash", iiaApproval.getConditionsHash());
                    map.put("conditionsHashPartner", iiaApproval.getConditionsHashPartner() == null ? "" : iiaApproval.getConditionsHashPartner());
                    return map;
                })
                .collect(Collectors.toSet());


        return javax.ws.rs.core.Response.ok(approvedHeiIds).build();
    }

    @GET
    @Path("get-partner-approvals")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response getPartnerApprovals(@QueryParam("iiaId") String iiaId) {
        if (iiaId == null || iiaId.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Iia iia = iiasEJB.findById(iiaId);
        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        String partnerHeiId = "";
        String partnerIiaId = "";
        String partnerIiaCode = "";
        String localHeiId = iiasEJB.getHeiId();
        for (CooperationCondition c : iia.getCooperationConditions()) {
            LOG.fine("get-partner-approvals: Sending Partner: " + c.getSendingPartner().getInstitutionId());
            LOG.fine("get-partner-approvals: Receiving Partner: " + c.getReceivingPartner().getInstitutionId());
            if (c.getSendingPartner().getInstitutionId().equals(localHeiId)) {
                partnerHeiId = c.getReceivingPartner().getInstitutionId();
                partnerIiaId = c.getReceivingPartner().getIiaId();
                partnerIiaCode = c.getReceivingPartner().getIiaCode();
            } else if (c.getReceivingPartner().getInstitutionId().equals(localHeiId)) {
                partnerHeiId = c.getSendingPartner().getInstitutionId();
                partnerIiaId = c.getSendingPartner().getIiaId();
                partnerIiaCode = c.getSendingPartner().getIiaCode();
            }
        }

        LOG.fine("get-partner-approvals: Sending Partner: " + partnerHeiId);

        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(partnerHeiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(registryClient.getIiaApprovalHeiUrls(partnerHeiId).get("url"));
        Map<String, List<String>> paramsMap = new HashMap<>();
        paramsMap.put("iia_id", Arrays.asList(iiaId));
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);
        LOG.fine("get-partner-approvals: Params: " + paramsMap);
        ClientResponse clientResponse = restClient.sendRequest(clientRequest, IiasApprovalResponse.class);
        LOG.fine("get-partner-approvals: Response: " + clientResponse);
        IiasApprovalResponse responseEnity = (IiasApprovalResponse) clientResponse.getResult();
        if (responseEnity == null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        for (IiasApprovalResponse.Approval approval : responseEnity.getApproval()) {
            if (approval.getIiaId().equals(iiaId)) {
                LOG.fine("get-partner-approvals: Approval found for IIA: " + iiaId);
                List<String> approvals = iiasEJB.findIiaApproval(iiaId)
                        .stream()
                        .map(IiaApproval::getHeiId)
                        .collect(Collectors.toList());

                if (!approvals.contains(partnerHeiId) && approval.getIiaHash().equals(iia.getHashPartner())) {
                    LOG.fine("get-partner-approvals: Approval not found for partner: " + partnerHeiId);
                    IiaApproval iiaApproval = new IiaApproval();
                    iiaApproval.setHeiId(partnerHeiId);
                    iiaApproval.setIia(iia);
                    iiaApproval.setConditionsHash(approval.getIiaHash());

                    iiasEJB.insertIiaApproval(iiaApproval);
                    LOG.fine("get-partner-approvals: Approval inserted for partner: " + partnerHeiId);
                }

                //send to algoria
                execNotificationToAlgoriaApprove(iiaId, partnerHeiId);
            }
        }

        LOG.fine("get-partner-approvals: Sending Partner: " + partnerHeiId);

        return Response.ok(responseEnity).build();
    }

    @POST
    @Path("approved-hash-sync")
    @InternalAuthenticate
    public javax.ws.rs.core.Response approvedHashSync() {

        CompletableFuture.runAsync(() -> {
            try {
                // your heavy work here
                generateFileSafely();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return javax.ws.rs.core.Response.ok().build();
    }

    @GET
    @Path("approved-hash-sync")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download() {
        if (!Files.exists(OUTPUT))
            throw new NotFoundException("File not ready yet");
        String filename = OUTPUT.getFileName().toString();
        return Response.ok(OUTPUT.toFile())
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
    }

    private void generateFileSafely() {
        try {
            List<Iia> approvedIias = iiasEJB.findApprovedVersions();
            //approvedIias = approvedIias.stream().filter(iia -> iia.getOriginal().getId().equals("D9C3D776-A72F-4FBB-8D72-6DAEB629A163")).collect(Collectors.toList());

            String localHeiId = iiasEJB.getHeiId();

            Files.createDirectories(OUTPUT.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(
                    OUTPUT, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                w.write("iia_id,our_hash,partner_hash,hash_in_partner_system\n");
                for (Iia iia : approvedIias) {
                    try {
                        List<String> hashInPartnerSystem = getHasshFromPartner(iia, localHeiId);
                        if (hashInPartnerSystem == null || !hashInPartnerSystem.contains(iia.getConditionsHash())) {
                            String line = iia.getOriginal().getId() + "," + iia.getConditionsHash() + "," + iia.getHashPartner() + "," + (hashInPartnerSystem != null ? String.join(";", hashInPartnerSystem) : "") + "\n";
                            w.write(line);
                        }
                    } catch (Exception e) {
                        w.write(iia.getOriginal().getId() + "," + iia.getConditionsHash() + "," + iia.getHashPartner() + ",ERROR_FETCHING_HASH\n");
                    }
                }
                w.write("iia_id,our_hash,partner_hash,hash_in_partner_system");
                w.flush();
            }
        } catch (IOException e) {
            logger.error("Failed to generate the export file", e);
        }
    }

    private List<String> getHasshFromPartner(Iia iia, String localHeiId) {
        String iiaId = iia.getOriginal().getId();

        String partnerHeiId = "";
        for (CooperationCondition c : iia.getCooperationConditions()) {
            if (c.getSendingPartner().getInstitutionId().equals(localHeiId)) {
                partnerHeiId = c.getReceivingPartner().getInstitutionId();
            } else if (c.getReceivingPartner().getInstitutionId().equals(localHeiId)) {
                partnerHeiId = c.getSendingPartner().getInstitutionId();
            }
        }


        ClientRequest clientRequest = new ClientRequest();
        clientRequest.setHeiId(partnerHeiId);
        clientRequest.setHttpsec(true);
        clientRequest.setMethod(HttpMethodEnum.GET);
        clientRequest.setUrl(registryClient.getIiaApprovalHeiUrls(partnerHeiId).get("url"));
        Map<String, List<String>> paramsMap = new HashMap<>();
        paramsMap.put("iia_id", Arrays.asList(iiaId));
        ParamsClass params = new ParamsClass();
        params.setUnknownFields(paramsMap);
        clientRequest.setParams(params);
        ClientResponse clientResponse = restClient.sendRequest(clientRequest, IiasApprovalResponse.class);
        IiasApprovalResponse responseEnity = (IiasApprovalResponse) clientResponse.getResult();
        if (responseEnity == null) {
            return null;
        }

        return responseEnity.getApproval().stream()
                .map(IiasApprovalResponse.Approval::getIiaHash)
                .collect(Collectors.toList());
    }


    @GET
    @Path("getApprovedHashes")
    @InternalAuthenticate
    public javax.ws.rs.core.Response getApprovedHashes(@QueryParam("iiaId") String iiaId) {
        Iia iia = iiasEJB.findById(iiaId);
        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        String localHeiId = iiasEJB.getHeiId();
        String iiaHash = iia.getConditionsHash();
        String partnerHash = iia.getHashPartner();


        ApprovedHashesDto dto = new ApprovedHashesDto();
        dto.setOurIiaHash(iiaHash);
        dto.setPartnerIiaHash(partnerHash);
        dto.setIiaId(iiaId);

        List<IiaApproval> approvals = iiasEJB.findIiaApproval(iiaId);

        String ourExact = null, ourFallback = null;
        String partnerExact = null, partnerFallback = null;

        for (IiaApproval approval : approvals) {
            String hash = approval.getConditionsHash();
            boolean isExact = iiaHash != null && (iiaHash.equals(hash) || partnerHash.equals(hash));

            if (approval.getHeiId().equals(localHeiId)) {
                if (isExact) {
                    ourExact = hash;                // lock in exact match
                } else if (ourFallback == null) {
                    ourFallback = hash;             // keep first seen as fallback
                }
            } else {
                if (isExact) {
                    partnerExact = hash;            // lock in exact match
                } else if (partnerFallback == null) {
                    partnerFallback = hash;         // keep first seen as fallback
                }
            }
        }

        String ourApprovalHash = (ourExact != null) ? ourExact : ourFallback;
        String partnerApprovalHash = (partnerExact != null) ? partnerExact : partnerFallback;

        dto.setOurApprovedHash(ourApprovalHash);
        dto.setPartnerApprovedHash(partnerApprovalHash);

        return javax.ws.rs.core.Response.ok(dto).build();
    }

    @DELETE
    @Path("shadow-delete")
    @InternalAuthenticate
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response shadowDelete(@QueryParam("iia_id") String iiaId, @QueryParam("force") Boolean force) {
        if (iiaId == null || iiaId.isEmpty()) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        Iia iia = iiasEJB.findById(iiaId);

        if (iia == null) {
            return javax.ws.rs.core.Response.status(Response.Status.NOT_FOUND).build();
        }

        if (!Boolean.TRUE.equals(force)  && iiasEJB.findApprovedVersion(iiaId) != null) {
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).entity("The IIA is approved").build();
        }

        iiasEJB.deleteIia(iia);

        return javax.ws.rs.core.Response.ok().build();
    }

    @GET
    @Path("getDuplicatedId")
    @InternalAuthenticate
    public javax.ws.rs.core.Response getDuplicatedId() {
        List<Iia> iias = iiasEJB.findAllNoneApproved();

        String localHeiId = iiasEJB.getHeiId();
        Map<String, Map<String, List<String>>> counts = new HashMap<>();

        for (Iia iia : iias) {
            IiaPartner partner = null;
            if (iia.getCooperationConditions() != null) {
                for (CooperationCondition condition : iia.getCooperationConditions()) {
                    if (condition.getSendingPartner().getInstitutionId() != null
                            && !condition.getSendingPartner().getInstitutionId().equals(localHeiId)) {
                        partner = condition.getSendingPartner();
                    }
                    if (condition.getReceivingPartner().getInstitutionId() != null
                            && !condition.getReceivingPartner().getInstitutionId().equals(localHeiId)) {
                        partner = condition.getReceivingPartner();
                    }
                }
            }

            if (partner != null) {
                if (!counts.containsKey(partner.getInstitutionId())) {
                    Map<String, List<String>> iiaList = new HashMap<>();
                    List<String> iiaIds = new ArrayList<>();
                    iiaIds.add(iia.getId());
                    iiaList.put(partner.getIiaId(), iiaIds);
                    counts.put(partner.getInstitutionId(), iiaList);
                } else {
                    Map<String, List<String>> iiaList = counts.get(partner.getInstitutionId());
                    if (iiaList.containsKey(partner.getIiaId())) {
                        iiaList.get(partner.getIiaId()).add(iia.getId());
                    } else {
                        List<String> iiaIds = new ArrayList<>();
                        iiaIds.add(iia.getId());
                        iiaList.put(partner.getIiaId(), iiaIds);
                    }

                }
            }
        }

        //filter only duplicated or if key is null
        counts.entrySet().removeIf(entry -> {
            Map<String, List<String>> iiaMap = entry.getValue();
            iiaMap.entrySet().removeIf(e -> e.getValue().size() < 2);
            iiaMap.entrySet().removeIf(e -> {
                String k = e.getKey();
                return k == null || k.trim().isEmpty() || k.trim().equalsIgnoreCase("null");
            });
            return iiaMap.isEmpty();
        });

        //iterate for each send get to partener
        counts.forEach((partnerHeiId, iiaMap) -> {
            iiaMap.forEach((partnerIiaId, ourIiaIds) -> {
                //send get to partnerHeiId with partnerIiaId
                try {
                    IiasGetResponse.Iia remoteIia = sendGet(partnerHeiId, partnerIiaId);
                    if (remoteIia != null) {
                        AtomicReference<String> ourIdFromPartner = new AtomicReference<>();
                        remoteIia.getPartner().forEach(p -> {
                            if (p.getHeiId().equals(localHeiId)) {
                                ourIdFromPartner.set(p.getIiaId());
                            }
                        });
                        //mark the correct one with an *
                        if (ourIdFromPartner.get() != null) {
                            ourIiaIds.forEach(ourIiaId -> {
                                if (ourIiaId.equals(ourIdFromPartner.get())) {
                                    ourIiaIds.set(ourIiaIds.indexOf(ourIiaId), ourIiaId + "*");
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error fetching remote IIA for partnerHeiId: " + partnerHeiId + " and partnerIiaId: " + partnerIiaId, e);
                }
            });
        });

        return javax.ws.rs.core.Response.ok(counts).build();
    }

    @POST
    @Path("massiveHashRecalc/start")
    @InternalAuthenticate
    public Response start(@Context UriInfo uriInfo) {
        String jobId = jobs.createJob(0);
        job.run(jobId);
        URI location = uriInfo.getAbsolutePathBuilder().path(jobId).build();
        return Response.accepted(new SimpleJobResponse(jobId, "accepted"))
                .location(location)
                .build();
    }

    @GET
    @Path("massiveHashRecalc/status/{jobId}")
    public Response status(@PathParam("jobId") String jobId) {
        JobInfo info = jobs.get(jobId);
        if (info == null) return Response.status(Response.Status.NOT_FOUND).build();
        return Response.ok(new StatusDto(jobId, info)).build();
    }

    @POST
    @Path("massiveHashRecalc/cancel/{jobId}")
    public Response cancel(@PathParam("jobId") String jobId) {
        JobInfo info = jobs.get(jobId);
        if (info == null) return Response.status(Response.Status.NOT_FOUND).build();
        jobs.cancel(jobId);
        return Response.ok(new SimpleJobResponse(jobId, "canceled")).build();
    }

    @POST
    @Path("getDuplicatedId/start")
    @InternalAuthenticate
    public Response startgetDuplicatedId(@Context UriInfo uriInfo) {
        String jobId = jobs.createJob(0);
        dupeJob.run(jobId, registryClient, restClient);
        URI location = uriInfo.getAbsolutePathBuilder().path(jobId).build();
        return Response.accepted(new SimpleJobResponse(jobId, "accepted"))
                .location(location)
                .build();
    }

    @GET
    @Path("getDuplicatedId/status/{jobId}")
    public Response statusgetDuplicatedId(@PathParam("jobId") String jobId) {
        JobInfo info = jobs.get(jobId);
        if (info == null) return Response.status(Response.Status.NOT_FOUND).build();
        if (info.status == JobStatus.COMPLETED) {
            return Response.ok(new StatusDto(jobId, info, dupeJob.getCounts())).build();
        }
        return Response.ok(new StatusDto(jobId, info)).build();
    }

    @POST
    @Path("getDuplicatedId/cancel/{jobId}")
    public Response cancelgetDuplicatedId(@PathParam("jobId") String jobId) {
        JobInfo info = jobs.get(jobId);
        if (info == null) return Response.status(Response.Status.NOT_FOUND).build();
        jobs.cancel(jobId);
        return Response.ok(new SimpleJobResponse(jobId, "canceled")).build();
    }

    // DTOs
    public static class SimpleJobResponse {
        public String jobId;
        public String status;

        public SimpleJobResponse(String jobId, String status) {
            this.jobId = jobId;
            this.status = status;
        }
    }

    public static class StatusDto {
        public String jobId;
        public String status;
        public int total;
        public int processed;
        public String error;
        public Long startedAt;
        public Long finishedAt;
        public Object result;

        public StatusDto(String id, JobInfo i) {
            jobId = id;
            status = i.status.name();
            total = i.total;
            processed = i.processed;
            error = i.error;
            startedAt = i.startedAt;
            finishedAt = i.finishedAt;
        }

        public StatusDto(String id, JobInfo i, Object result) {
            jobId = id;
            status = i.status.name();
            total = i.total;
            processed = i.processed;
            error = i.error;
            startedAt = i.startedAt;
            finishedAt = i.finishedAt;
            this.result = result;
        }
    }
}
