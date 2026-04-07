package eu.erasmuswithoutpaper.omobility.las.boundary;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.architecture.MultilineStringWithOptionalLang;
import eu.erasmuswithoutpaper.api.omobilities.las.cnr.endpoints.stats.LasIncomingStatsResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.LasOutgoingStatsResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.LearningAgreement;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasGetResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasIndexResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasUpdateRequest;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasUpdateResponse;
import eu.erasmuswithoutpaper.common.boundary.ClientRequest;
import eu.erasmuswithoutpaper.common.boundary.ClientResponse;
import eu.erasmuswithoutpaper.common.boundary.HttpMethodEnum;
import eu.erasmuswithoutpaper.common.boundary.ParamsClass;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;
import eu.erasmuswithoutpaper.common.control.RegistryClient;
import eu.erasmuswithoutpaper.common.control.RestClient;
import eu.erasmuswithoutpaper.error.control.EwpWebApplicationException;
import eu.erasmuswithoutpaper.imobility.entity.IMobility;
import eu.erasmuswithoutpaper.imobility.entity.IMobilityStatus;
import eu.erasmuswithoutpaper.omobility.las.control.LearningAgreementEJB;
import eu.erasmuswithoutpaper.omobility.las.control.OutgoingMobilityLearningAgreementsConverter;
import eu.erasmuswithoutpaper.omobility.las.entity.*;
import eu.erasmuswithoutpaper.omobility.las.dto.AlgoriaOmobilityLasIndexDto;
import eu.erasmuswithoutpaper.organization.entity.Institution;
import eu.erasmuswithoutpaper.security.EwpAuthenticate;
import eu.erasmuswithoutpaper.security.InternalAuthenticate;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;

@Path("omobilities/las")
public class OutgoingMobilityLearningAgreementsResource {

    @EJB
    LearningAgreementEJB learningAgreementEJB;

    @Inject
    GlobalProperties properties;

    @Inject
    OutgoingMobilityLearningAgreementsConverter converter;

    @Inject
    RegistryClient registryClient;

    @Context
    HttpServletRequest httpRequest;

    @Inject
    OmobilitiesLasAuxThread ait;

    @Inject
    RestClient restClient;

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(OutgoingMobilityLearningAgreementsResource.class.getCanonicalName());

    /*@GET
    @Path("index")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response indexGet(@QueryParam("sending_hei_id") List<String> sendingHeiIds, @QueryParam("receiving_hei_id") List<String> receivingHeiIdList, @QueryParam("receiving_academic_year_id") List<String> receiving_academic_year_ids,
                                              @QueryParam("global_id") List<String> globalIds, @QueryParam("mobility_type") List<String> mobilityTypes, @QueryParam("modified_since") List<String> modifiedSinces) {
        return omobilityLasIndex(sendingHeiIds, receivingHeiIdList, receiving_academic_year_ids, globalIds, mobilityTypes, modifiedSinces);
    }

    @POST
    @Path("index")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response indexPost(@FormParam("sending_hei_id") List<String> sendingHeiIds, @FormParam("receiving_hei_id") List<String> receivingHeiIdList, @FormParam("receiving_academic_year_id") List<String> receiving_academic_year_ids,
                                               @FormParam("global_id") List<String> globalIds, @FormParam("mobility_type") List<String> mobilityTypes, @FormParam("modified_since") List<String> modifiedSinces) {
        return omobilityLasIndex(sendingHeiIds, receivingHeiIdList, receiving_academic_year_ids, globalIds, mobilityTypes, modifiedSinces);
    }*/

    @GET
    @Path("index")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response indexGet(@QueryParam("sending_hei_id") List<String> sendingHeiIds, @QueryParam("receiving_hei_id") List<String> receivingHeiIdList, @QueryParam("receiving_academic_year_id") List<String> receiving_academic_year_ids,
                                              @QueryParam("global_id") List<String> globalIds, @QueryParam("mobility_type") List<String> mobilityTypes, @QueryParam("modified_since") List<String> modifiedSinces) {
        return omobilityLasIndexAlgoria(sendingHeiIds, receivingHeiIdList, receiving_academic_year_ids, globalIds, mobilityTypes, modifiedSinces);
    }

    @POST
    @Path("index")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response indexPost(@FormParam("sending_hei_id") List<String> sendingHeiIds, @FormParam("receiving_hei_id") List<String> receivingHeiIdList, @FormParam("receiving_academic_year_id") List<String> receiving_academic_year_ids,
                                               @FormParam("global_id") List<String> globalIds, @FormParam("mobility_type") List<String> mobilityTypes, @FormParam("modified_since") List<String> modifiedSinces) {
        return omobilityLasIndexAlgoria(sendingHeiIds, receivingHeiIdList, receiving_academic_year_ids, globalIds, mobilityTypes, modifiedSinces);
    }

    @GET
    @Path("index_test")
    @Produces(MediaType.APPLICATION_XML)
    @InternalAuthenticate
    public javax.ws.rs.core.Response indexPostTest(@FormParam("sending_hei_id") List<String> sendingHeiIds, @FormParam("receiving_hei_id") List<String> receivingHeiIdList, @FormParam("receiving_academic_year_id") List<String> receiving_academic_year_ids,
                                               @FormParam("global_id") List<String> globalIds, @FormParam("mobility_type") List<String> mobilityTypes, @FormParam("modified_since") List<String> modifiedSinces) {
        LOG.info("---- START /omobilities/las/index_test ----");
        return omobilityLasIndexAlgoria(sendingHeiIds, receivingHeiIdList, receiving_academic_year_ids, globalIds, mobilityTypes, modifiedSinces);
    }

    /*@GET
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityGetGet(@QueryParam("sending_hei_id") List<String> sendingHeiId, @QueryParam("omobility_id") List<String> mobilityIdList) {
        return mobilityGet(sendingHeiId, mobilityIdList);
    }

    @POST
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityGetPost(@FormParam("sending_hei_id") List<String> sendingHeiId, @FormParam("omobility_id") List<String> mobilityIdList) {
        return mobilityGet(sendingHeiId, mobilityIdList);
    }*/

    @GET
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityGetGet(@QueryParam("sending_hei_id") List<String> sendingHeiId, @QueryParam("omobility_id") List<String> mobilityIdList) {
        return mobilityGetAlgoria(sendingHeiId, mobilityIdList);
    }

    @POST
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityGetPost(@FormParam("sending_hei_id") List<String> sendingHeiId, @FormParam("omobility_id") List<String> mobilityIdList) {
        return mobilityGetAlgoria(sendingHeiId, mobilityIdList);
    }

    @GET
    @Path("get_test")
    @Produces(MediaType.APPLICATION_XML)
    @InternalAuthenticate
    public javax.ws.rs.core.Response omobilityGetPostTest(@QueryParam("omobility_id") List<String> mobilityIdList, @QueryParam("reciving_hei_id") String recivingHeiId) {
        LOG.info("---- START /omobilities/las/get_test ----");
        return mobilityGetAlgoria(Collections.singletonList("uma.es"), mobilityIdList, recivingHeiId);
    }

    /*@POST
    @Path("update")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityLasUpdatePost(OmobilityLasUpdateRequest request) {
        if (request == null) {
            throw new EwpWebApplicationException("No update data was sent", Response.Status.BAD_REQUEST);
        }

        if (request.getSendingHeiId() == null || request.getSendingHeiId().isEmpty()) {
            throw new EwpWebApplicationException("Mising required parameter, sending-hei-id is required", Response.Status.BAD_REQUEST);
        }

        if (request.getCommentProposalV1() == null && request.getApproveProposalV1() == null) {
            throw new EwpWebApplicationException("Mising required parameter, approve-proposal-v1 and comment-proposal-v1 both of them can not be missing", Response.Status.BAD_REQUEST);
        }

        if (request.getApproveProposalV1() != null) {
            if (request.getApproveProposalV1().getOmobilityId() == null || request.getApproveProposalV1().getOmobilityId().isEmpty()) {
                throw new EwpWebApplicationException("Mising required parameter, omobility-id is required", Response.Status.BAD_REQUEST);
            }

            if (request.getApproveProposalV1().getChangesProposalId() == null) {
                throw new EwpWebApplicationException("Mising required parameter, changes-proposal-id is required", Response.Status.BAD_REQUEST);
            }

            LOG.fine("Starting UPD-ACCEPT for " + request.getApproveProposalV1().getOmobilityId() + " omobility learning agreements");

            OlearningAgreement olearningAgreement = learningAgreementEJB.findById(request.getApproveProposalV1().getOmobilityId());

            if (olearningAgreement == null) {
                throw new EwpWebApplicationException("Learning agreement does not exist", Response.Status.BAD_REQUEST);
            } else {
                if (olearningAgreement.getChangesProposal() == null) {
                    throw new EwpWebApplicationException("Changes proposal does not exist", Response.Status.BAD_REQUEST);
                }
                if (!olearningAgreement.getChangesProposal().getId().equals(request.getApproveProposalV1().getChangesProposalId())) {
                    throw new EwpWebApplicationException("Changes proposal does not match", Response.Status.CONFLICT);
                }
            }

            learningAgreementEJB.approveChangesProposal(request, request.getApproveProposalV1().getOmobilityId());
        } else if (request.getCommentProposalV1() != null) {
            if (request.getCommentProposalV1().getOmobilityId() == null || request.getCommentProposalV1().getOmobilityId().isEmpty()) {
                throw new EwpWebApplicationException("Mising required parameter, omobility-id is required", Response.Status.BAD_REQUEST);
            }

            if (request.getCommentProposalV1().getChangesProposalId() == null) {
                throw new EwpWebApplicationException("Mising required parameter, changes-proposal-id is required", Response.Status.BAD_REQUEST);
            }

            if (request.getCommentProposalV1().getComment() == null) {
                throw new EwpWebApplicationException("Mising required parameter, comment is required", Response.Status.BAD_REQUEST);
            }

            LOG.fine("Starting UPD-REJECT for " + request.getCommentProposalV1().getOmobilityId() + " omobility learning agreements");

            OlearningAgreement olearningAgreement = learningAgreementEJB.findById(request.getCommentProposalV1().getOmobilityId());

            if (olearningAgreement == null) {
                throw new EwpWebApplicationException("Learning agreement does not exist", Response.Status.BAD_REQUEST);
            } else {
                if (olearningAgreement.getChangesProposal() == null) {
                    throw new EwpWebApplicationException("Changes proposal does not exist", Response.Status.BAD_REQUEST);
                }
                if (!olearningAgreement.getChangesProposal().getId().equals(request.getCommentProposalV1().getChangesProposalId())) {
                    throw new EwpWebApplicationException("Changes proposal does not match", Response.Status.CONFLICT);
                }
            }

            learningAgreementEJB.rejectChangesProposal(request, request.getCommentProposalV1().getOmobilityId());
        }

        OmobilityLasUpdateResponse response = new OmobilityLasUpdateResponse();
        MultilineStringWithOptionalLang message = new MultilineStringWithOptionalLang();
        message.setLang("en");
        message.setValue("The update request was received with success.");
        response.getSuccessUserMessage().add(message);

        return javax.ws.rs.core.Response.ok(response).build();
    }*/

    @POST
    @Path("update")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityLasUpdatePostAlgoria(OmobilityLasUpdateRequest request) {
        if (request == null) {
            throw new EwpWebApplicationException("No update data was sent", Response.Status.BAD_REQUEST);
        }
        if (request.getSendingHeiId() == null || request.getSendingHeiId().isEmpty()) {
            throw new EwpWebApplicationException("Mising required parameter, sending-hei-id is required", Response.Status.BAD_REQUEST);
        }
        if (request.getApproveProposalV1() == null && request.getCommentProposalV1() == null) {
            throw new EwpWebApplicationException("Mising required parameter, approve-proposal-v1 and comment-proposal-v1 both of them can not be missing", Response.Status.BAD_REQUEST);
        }

        Collection<String> heisCoveredByCertificate;
        if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
            heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
        } else {
            heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
        }

        if (heisCoveredByCertificate.isEmpty()) {
            return javax.ws.rs.core.Response.ok(new OmobilityLasIndexResponse()).build();
        }

        String recivingHeiId = heisCoveredByCertificate.iterator().next();

        String omobilityId = null;
        String action = null;
        if (request.getApproveProposalV1() != null) {
            omobilityId = request.getApproveProposalV1().getOmobilityId();
            action = "approve";
        } else if (request.getCommentProposalV1() != null) {
            omobilityId = request.getCommentProposalV1().getOmobilityId();
            action = "reject";
        }

        if (omobilityId == null || omobilityId.isEmpty()) {
            throw new EwpWebApplicationException("Mising required parameter, omobility-id is required", Response.Status.BAD_REQUEST);
        }

        String url = properties.getAlgoriaOmobilityByIDLasUrl(recivingHeiId, omobilityId) + action + "/";

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addSerializer(XMLGregorianCalendar.class, new JsonSerializer<XMLGregorianCalendar>() {
            @Override
            public void serialize(XMLGregorianCalendar value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                if (value == null) {
                    gen.writeNull();
                    return;
                }
                gen.writeString(value.toXMLFormat());
            }
        });
        mapper.registerModule(module);

        try {
            JsonNode node = mapper.valueToTree(request);
            pruneNulls(node);
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            LOG.info("Algoria update. URL: " + url + "\nJSON body:\n" + json);

            String token = properties.getAlgoriaAuthotizationToken();
            Response algoriaResponse = ClientBuilder.newBuilder()
                    .build()
                    .target(url.trim())
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header("Authorization", token)
                    .post(Entity.json(json));
            try {
                String rawBody = algoriaResponse.readEntity(String.class);
                if (algoriaResponse.getStatus() < 200 || algoriaResponse.getStatus() >= 300) {
                    LOG.warning("Algoria update failed. HTTP " + algoriaResponse.getStatus() + ". Response body:\n" + rawBody);
                    throw new EwpWebApplicationException("Update failed. HTTP " + algoriaResponse.getStatus(), Response.Status.BAD_GATEWAY);
                }
                try {
                    JsonNode respNode = mapper.readTree(rawBody);
                    JsonNode successNode = respNode.get("success");
                    if (successNode == null || !successNode.isBoolean()) {
                        throw new EwpWebApplicationException("Update failed.", Response.Status.BAD_GATEWAY);
                    }
                    if (!successNode.asBoolean()) {
                        throw new EwpWebApplicationException("Update failed", Response.Status.BAD_GATEWAY);
                    }
                } catch (EwpWebApplicationException e) {
                    throw e;
                } catch (Exception e) {
                    throw new EwpWebApplicationException("Update failed", Response.Status.BAD_GATEWAY);
                }
            } finally {
                algoriaResponse.close();
            }
        } catch (Exception e) {
            if (e instanceof EwpWebApplicationException) {
                throw (EwpWebApplicationException) e;
            }
            LOG.warning("Algoria update failed: " + e.getMessage());
            throw new EwpWebApplicationException("Update failed", Response.Status.BAD_GATEWAY);
        }

        OmobilityLasUpdateResponse response = new OmobilityLasUpdateResponse();
        MultilineStringWithOptionalLang message = new MultilineStringWithOptionalLang();
        message.setLang("en");
        message.setValue("Updated.");
        response.getSuccessUserMessage().add(message);

        return javax.ws.rs.core.Response.ok(response).build();
    }


    /*@GET
    @Path("stats")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityGetStats() {
        List<Institution> institutionList = learningAgreementEJB.getInternalInstitution();
        if (institutionList.size() != 1) {
            throw new IllegalStateException("Internal error: more than one insitution covered");
        }
        String heiId = institutionList.get(0).getId();

        return omobilityStatsGet(heiId);
    }*/

    @GET
    @Path("stats")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityGetStatsAlgoria() {
        LOG.info("---- START /omobilities/las/test_stats ----");

        String url = properties.getAlgoriaOmobilityLasStatsUrl();
        String token = properties.getAlgoriaAuthotizationToken();
        LOG.info("Algoria stats outbound method=GET");
        LOG.info("Algoria stats outbound url=" + url);
        LOG.info("Algoria stats outbound header Authorization=" + token);

        Response algoriaResponse = ClientBuilder.newBuilder().build().target(url.trim()).request().header("Authorization", token).get();
        String rawBody = algoriaResponse.readEntity(String.class);
        try {
            LOG.info("Algoria stats response status=" + algoriaResponse.getStatus());
            LOG.info("Algoria stats response headers=" + algoriaResponse.getStringHeaders());
            LOG.info("Algoria stats raw body:\n" + rawBody);
            if (algoriaResponse.getStatus() < 200 || algoriaResponse.getStatus() >= 300) {
                throw new EwpWebApplicationException("Stats request failed. HTTP " + algoriaResponse.getStatus(), Response.Status.BAD_GATEWAY);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            JsonNode root = mapper.readTree(rawBody);
            JsonNode statsNode = root.get("academicYearLaStats");
            if (statsNode != null && statsNode.isArray() && statsNode.size() == 1 && statsNode.get(0).isArray()) {
                ((ObjectNode) root).set("academicYearLaStats", statsNode.get(0));
                statsNode = root.get("academicYearLaStats");
            }
            if (statsNode != null && statsNode.isArray()) {
                for (JsonNode statNode : statsNode) {
                    if (statNode.isObject()) {
                        JsonNode yearNode = statNode.get("receivingAcademicYearId");
                        if (yearNode != null && yearNode.isTextual()) {
                            ((ObjectNode) statNode).put("receivingAcademicYearId", normalizeAcademicYearId(yearNode.asText()));
                        }
                    }
                }
            }

            LasOutgoingStatsResponse response = mapper.convertValue(root, LasOutgoingStatsResponse.class);
            LOG.info("Algoria stats mapped response: " + mapper.writeValueAsString(response));
            return javax.ws.rs.core.Response.ok(response).build();
        } catch (EwpWebApplicationException e) {
            LOG.warning("Algoria stats failed with known error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.warning("Algoria stats response (" + algoriaResponse.getStatus() + ") raw:\n" + rawBody);
            LOG.warning("Algoria stats parse error: " + e.getMessage());
            throw new EwpWebApplicationException("Stats request failed", Response.Status.BAD_GATEWAY);
        } finally {
            algoriaResponse.close();
        }
    }

    /*@POST
    @Path("cnr")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilitiesLasCnr(@FormParam("sending_hei_id") String sendingHeiId, @FormParam("omobility_id") List<String> omobilityIdList) {
        if (sendingHeiId == null || sendingHeiId.trim().isEmpty()) {
            throw new EwpWebApplicationException("Missing argumanets for get.", Response.Status.BAD_REQUEST);
        }

        if (omobilityIdList.size() > properties.getMaxOmobilitylasIds()) {
            throw new EwpWebApplicationException("Max number of omobility learning agreements id's has exceeded.", Response.Status.BAD_REQUEST);
        }

        //TODO: Notify algoria

        LOG.info("Starting CNR for " + omobilityIdList.size() + " omobility learning agreements");

        for (String omobilityId : omobilityIdList) {
            CompletableFuture.runAsync(() -> {
                try {
                    ait.createLas(sendingHeiId, omobilityId);
                } catch (Exception e) {
                    LOG.fine("Error in AuxIiaApprovalThread: " + e.getMessage());
                }
            });
        }

        eu.erasmuswithoutpaper.api.omobilities.las.cnr.endpoints.ObjectFactory factory = new eu.erasmuswithoutpaper.api.omobilities.las.cnr.endpoints.ObjectFactory();

        return javax.ws.rs.core.Response.ok(factory.createOmobilityLaCnrResponse(new Empty())).build();
    }*/

    @POST
    @Path("cnr")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilitiesLasCnrAlgoria(@FormParam("sending_hei_id") String sendingHeiId, @FormParam("omobility_id") List<String> omobilityIdList) {
        if (sendingHeiId == null || sendingHeiId.trim().isEmpty()) {
            throw new EwpWebApplicationException("Missing argumanets for get.", Response.Status.BAD_REQUEST);
        }

        if (omobilityIdList.size() > properties.getMaxOmobilitylasIds()) {
            throw new EwpWebApplicationException("Max number of omobility learning agreements id's has exceeded.", Response.Status.BAD_REQUEST);
        }

        CompletableFuture.runAsync(() -> {
            for (String omobilityId : omobilityIdList) {
                try {
                    ait.createLasAlgoria(sendingHeiId, omobilityId);
                } catch (Exception e) {
                    LOG.fine("Error in AuxIiaApprovalThread: " + e.getMessage());
                }
            }
        });



        eu.erasmuswithoutpaper.api.omobilities.las.cnr.endpoints.ObjectFactory factory = new eu.erasmuswithoutpaper.api.omobilities.las.cnr.endpoints.ObjectFactory();

        return javax.ws.rs.core.Response.ok(factory.createOmobilityLaCnrResponse(new Empty())).build();
    }

    /*@GET
    @Path("cnr/stats")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityGetStatsCnrAlgoria() {
        LOG.info("---- START /omobilities/las/test_stats ----");

        String url = properties.getAlgoriaOmobilityLasStatsCnrUrl();
        String token = properties.getAlgoriaAuthotizationToken();
        LOG.info("Algoria cnr/stats outbound method=GET");
        LOG.info("Algoria cnr/stats outbound url=" + url);
        LOG.info("Algoria cnr/stats outbound header Authorization=" + token);

        Response algoriaResponse = ClientBuilder.newBuilder().build().target(url.trim()).request().header("Authorization", token).get();
        String rawBody = algoriaResponse.readEntity(String.class);
        try {
            LOG.info("Algoria cnr/stats response status=" + algoriaResponse.getStatus());
            LOG.info("Algoria cnr/stats response headers=" + algoriaResponse.getStringHeaders());
            LOG.info("Algoria cnr/stats raw body:\n" + rawBody);
            if (algoriaResponse.getStatus() < 200 || algoriaResponse.getStatus() >= 300) {
                throw new EwpWebApplicationException("Stats request failed. HTTP " + algoriaResponse.getStatus(), Response.Status.BAD_GATEWAY);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            JsonNode root = mapper.readTree(rawBody);
            JsonNode statsNode = root.get("academicYearLaStats");
            if (statsNode != null && statsNode.isArray() && statsNode.size() == 1 && statsNode.get(0).isArray()) {
                ((ObjectNode) root).set("academicYearLaStats", statsNode.get(0));
                statsNode = root.get("academicYearLaStats");
            }
            if (statsNode != null && statsNode.isArray()) {
                for (JsonNode statNode : statsNode) {
                    if (statNode.isObject()) {
                        JsonNode yearNode = statNode.get("receivingAcademicYearId");
                        if (yearNode != null && yearNode.isTextual()) {
                            ((ObjectNode) statNode).put("receivingAcademicYearId", normalizeAcademicYearId(yearNode.asText()));
                        }
                    }
                }
            }

            LasIncomingStatsResponse response = mapper.convertValue(root, LasIncomingStatsResponse.class);
            LOG.info("Algoria stats mapped response: " + mapper.writeValueAsString(response));
            return javax.ws.rs.core.Response.ok(response).build();
        } catch (EwpWebApplicationException e) {
            LOG.warning("Algoria stats failed with known error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.warning("Algoria stats response (" + algoriaResponse.getStatus() + ") raw:\n" + rawBody);
            LOG.warning("Algoria stats parse error: " + e.getMessage());
            throw new EwpWebApplicationException("Stats request failed", Response.Status.BAD_GATEWAY);
        } finally {
            algoriaResponse.close();
        }
    }*/

    /*private javax.ws.rs.core.Response omobilityStatsGet(String heiId) {
        LasOutgoingStatsResponse response = new LasOutgoingStatsResponse();

        //Filter learning agreement 
        List<OlearningAgreement> omobilityLasList = learningAgreementEJB.findByReceivingHeiId(heiId);

        if (!omobilityLasList.isEmpty()) {

            Collection<String> heisCoveredByCertificate;
            if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
                heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
            } else {
                heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
            }

            //checking if caller covers the receiving HEI of this mobility,
            omobilityLasList = omobilityLasList.stream().filter(omobility -> heisCoveredByCertificate.contains(omobility.getReceivingHei().getHeiId())).collect(Collectors.toList());

            //Grouping learning agreement by year             
            Map<String, List<OlearningAgreement>> olearningAgreementGroupByYear = new HashMap<String, List<OlearningAgreement>>();
            omobilityLasList.forEach(olas -> {
                Set<String> keys = olearningAgreementGroupByYear.keySet();

                String[] years = olas.getReceivingAcademicTermEwpId().split("/");
                if ((Integer.parseInt(years[0]) >= Integer.parseInt("2021")) && (Integer.parseInt(years[1]) >= Integer.parseInt("2022"))) {
                    if (keys.contains(olas.getReceivingAcademicTermEwpId())) {
                        List<OlearningAgreement> group = olearningAgreementGroupByYear.get(olas.getReceivingAcademicTermEwpId());

                        group.add(olas);
                        olearningAgreementGroupByYear.put(olas.getReceivingAcademicTermEwpId(), group);
                    } else {
                        List<OlearningAgreement> group = new ArrayList<>();
                        group.add(olas);
                        olearningAgreementGroupByYear.put(olas.getReceivingAcademicTermEwpId(), group);
                    }
                }

            });

            //Calculate stats
            List<AcademicYearLaStats> stats = new ArrayList<>();
            Set<String> keys = olearningAgreementGroupByYear.keySet();

            keys.forEach(key -> {
                List<OlearningAgreement> group = olearningAgreementGroupByYear.get(key);

                AcademicYearLaStats currentGroupStat = new AcademicYearLaStats();
                currentGroupStat.setReceivingAcademicYearId(key);
                currentGroupStat.setLaOutgoingTotal(BigInteger.valueOf(group.size()));

                int countNotModifiedAfterApproval = 0;
                int countModifiedAfterApproval = 0;
                int countLatestVersionApproved = 0;
                int countLatestVersionRejected = 0;
                int countLatestVersionawaiting = 0;

                for (OlearningAgreement g : group) {
                    if (g.getApprovedChanges() == null) {//Does not have other versions
                        countNotModifiedAfterApproval++;
                    }

                    if (g.getApprovedChanges() != null) {//Does have other versions
                        countModifiedAfterApproval++;
                    }

                    if (g.getApprovedChanges().getReceivingHeiSignature() != null) {
                        countLatestVersionApproved++;
                    }

                    //Find pending and rejected mobilities
                    List<IMobility> imobilities = learningAgreementEJB.findIMobilityByOmobilityId(g.getId());
                    if (imobilities != null & !imobilities.isEmpty()) {
                        for (IMobility imobility : imobilities) {
                            if (imobility.getIstatus().equals(IMobilityStatus.REJECTED)) {
                                countLatestVersionRejected++;
                            }

                            if (imobility.getIstatus().equals(IMobilityStatus.PENDING)) {
                                countLatestVersionawaiting++;
                            }
                        }

                    }
                }

                currentGroupStat.setLaOutgoingLatestVersionApproved(BigInteger.valueOf(countLatestVersionApproved));
                currentGroupStat.setLaOutgoingLatestVersionAwaiting(BigInteger.valueOf(countLatestVersionawaiting));
                currentGroupStat.setLaOutgoingLatestVersionRejected(BigInteger.valueOf(countLatestVersionRejected));
                currentGroupStat.setLaOutgoingModifiedAfterApproval(BigInteger.valueOf(countModifiedAfterApproval));
                currentGroupStat.setLaOutgoingNotModifiedAfterApproval(BigInteger.valueOf(countNotModifiedAfterApproval));

                stats.add(currentGroupStat);
            });

            response.getAcademicYearLaStats().addAll(omobilitiesLasStats(stats));
        }

        return javax.ws.rs.core.Response.ok(response).build();
    }*/

    /*private List<LasOutgoingStatsResponse.AcademicYearLaStats> omobilitiesLasStats(List<AcademicYearLaStats> academicYearStats) {
        List<LasOutgoingStatsResponse.AcademicYearLaStats> omobilitiesLasStats = new ArrayList<>();
        academicYearStats.stream().forEachOrdered((m) -> {
            omobilitiesLasStats.add(converter.convertToLearningAgreementsStats(m));
        });

        return omobilitiesLasStats;
    }*/

    /*private javax.ws.rs.core.Response mobilityGet(List<String> sendingHeiIds, List<String> mobilityIdList) {
        if (sendingHeiIds != null && sendingHeiIds.size() > 1) {
            throw new EwpWebApplicationException("Only one sending HEI ID is allowed.", Response.Status.BAD_REQUEST);
        }
        if (sendingHeiIds == null || sendingHeiIds.isEmpty()) {
            throw new EwpWebApplicationException("Missing sending HEI ID.", Response.Status.BAD_REQUEST);
        }
        String sendingHeiId = sendingHeiIds.get(0);
        if (sendingHeiId == null || sendingHeiId.trim().isEmpty() || mobilityIdList == null || mobilityIdList.isEmpty()) {
            throw new EwpWebApplicationException("Missing argumanets for get.", Response.Status.BAD_REQUEST);
        }
        LOG.fine("sendingHeiId: " + sendingHeiId);

        if (mobilityIdList.size() > properties.getMaxOmobilitylasIds()) {
            throw new EwpWebApplicationException("Max number of omobility learning agreements id's has exceeded.", Response.Status.BAD_REQUEST);
        }

        LOG.fine("mobilityIdList: " + mobilityIdList.toString());

        OmobilityLasGetResponse response = new OmobilityLasGetResponse();
        List<OlearningAgreement> omobilityLasList = learningAgreementEJB.findBySendingHeiIdFilterd(sendingHeiId);
        LOG.fine("omobilityLasList: " + omobilityLasList.toString());

        if (!omobilityLasList.isEmpty()) {


            //checking if caller covers the receiving HEI of this mobility,
            //omobilityLasList = omobilityLasList.stream().filter(omobility -> heisCoveredByCertificate.contains(omobility.getReceivingHei().getHeiId())).collect(Collectors.toList());
            LOG.fine("GETREQUEST FROM PARTNER: FOR:" + omobilityLasList.get(0).getId());

            response.getLa().addAll(omobilitiesLas(omobilityLasList, mobilityIdList));
        } else {
            LOG.fine("omobilityLasList is empty");
            return javax.ws.rs.core.Response.status(Response.Status.BAD_REQUEST).build();
        }

        return javax.ws.rs.core.Response.ok(response).build();
    }*/

    private javax.ws.rs.core.Response mobilityGetAlgoria(List<String> sendingHeiIds, List<String> mobilityIdList) {
        Collection<String> heisCoveredByCertificate;
        if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
            heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
        } else {
            heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
        }

        if (heisCoveredByCertificate.isEmpty()) {
            throw new EwpWebApplicationException("No HEIs covered by this certificate.", Response.Status.FORBIDDEN);
        }

        String receivingHeiId = heisCoveredByCertificate.iterator().next();

        return mobilityGetAlgoria(sendingHeiIds, mobilityIdList, receivingHeiId);
    }

    private javax.ws.rs.core.Response mobilityGetAlgoria(List<String> sendingHeiIds, List<String> mobilityIdList, String receivingHeiId) {
        if (sendingHeiIds != null && sendingHeiIds.size() > 1) {
            throw new EwpWebApplicationException("Only one sending HEI ID is allowed.", Response.Status.BAD_REQUEST);
        }
        if (sendingHeiIds == null || sendingHeiIds.isEmpty()) {
            throw new EwpWebApplicationException("Missing sending HEI ID.", Response.Status.BAD_REQUEST);
        }
        String sendingHeiId = sendingHeiIds.get(0);
        if (sendingHeiId == null || sendingHeiId.trim().isEmpty() || mobilityIdList == null || mobilityIdList.isEmpty()) {
            throw new EwpWebApplicationException("Missing argumanets for get.", Response.Status.BAD_REQUEST);
        }
        LOG.fine("sendingHeiId: " + sendingHeiId);

        if (mobilityIdList.size() > properties.getMaxOmobilitylasIds()) {
            throw new EwpWebApplicationException("Max number of omobility learning agreements id's has exceeded.", Response.Status.BAD_REQUEST);
        }


        LOG.fine("mobilityIdList: " + mobilityIdList.toString());

        OmobilityLasGetResponse response = new OmobilityLasGetResponse();
        String token = properties.getAlgoriaAuthotizationToken();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        for (String mobilityId : mobilityIdList) {
            String url = properties.getAlgoriaOmobilityByIDLasUrl(receivingHeiId, mobilityId);
            LOG.fine("Algoria GET URL: " + url);
            WebTarget target = ClientBuilder.newBuilder().build().target(url.trim());
            Response algoriaResponse = target.request().header("Authorization", token).get();
            String rawBody = algoriaResponse.readEntity(String.class);
            try {
                JsonNode root = mapper.readTree(rawBody);

                JsonNode laNode = root.get("la");
                if (laNode != null && laNode.isObject()) {
                    ObjectNode laObject = (ObjectNode) laNode;

                    normalizeReceivingHeiContactPerson(laObject);
                    normalizeDates(laObject);
                    normalizeComponents(laObject.get("firstVersion"));
                    normalizeComponents(laObject.get("approvedChanges"));
                    normalizeComponents(laObject.get("changesProposal"));

                    LearningAgreement la = mapper.treeToValue(laObject, LearningAgreement.class);
                    stripDateTimezones(la);
                    response.getLa().add(la);
                }
            } catch (Exception e) {
                LOG.warning("Algoria get response (" + algoriaResponse.getStatus() + ") for " + mobilityId + " raw:\n" + rawBody);
                LOG.warning("Algoria get parse error for " + mobilityId + ": " + e.getMessage());
            } finally {
                algoriaResponse.close();
            }
        }

        return javax.ws.rs.core.Response.ok(response).build();
    }

    private void stripDateTimezones(LearningAgreement la) {
        if (la == null) {
            return;
        }
        stripTimezone(la.getStartDate());
        stripTimezone(la.getEndDate());
        if (la.getStudent() != null) {
            stripTimezone(la.getStudent().getBirthDate());
        }
    }

    private void stripTimezone(XMLGregorianCalendar cal) {
        if (cal != null) {
            cal.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
        }
    }

    private String normalizeAcademicYearId(String academicYearId) {
        if (academicYearId == null) {
            return null;
        }
        String trimmed = academicYearId.trim();
        String[] parts = trimmed.split("/");
        if (parts.length == 2 && parts[0].matches("\\d{4}") && parts[1].matches("\\d{2}")) {
            int startYear = Integer.parseInt(parts[0]);
            return startYear + "/" + (startYear + 1);
        }
        return trimmed;
    }

    private void pruneNulls(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> toRemove = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    toRemove.add(entry.getKey());
                } else {
                    pruneNulls(value);
                }
            }
            if (!toRemove.isEmpty()) {
                obj.remove(toRemove);
            }
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (int i = arr.size() - 1; i >= 0; i--) {
                JsonNode value = arr.get(i);
                if (value == null || value.isNull()) {
                    arr.remove(i);
                } else {
                    pruneNulls(value);
                }
            }
        }
    }

    private void normalizeReceivingHeiContactPerson(ObjectNode laObject) {
        JsonNode receivingHei = laObject.get("receivingHei");
        if (receivingHei != null && receivingHei.isObject()) {
            ObjectNode receivingHeiObj = (ObjectNode) receivingHei;
            JsonNode contactPerson = receivingHeiObj.get("contact-person");
            if (contactPerson != null && receivingHeiObj.get("contactPerson") == null) {
                receivingHeiObj.set("contactPerson", contactPerson);
                receivingHeiObj.remove("contact-person");
            }
        }
    }

    private void normalizeDates(ObjectNode laObject) {
        // Algoria sends dateTime for date fields, trim to yyyy-MM-dd
        normalizeDateField(laObject, "startDate");
        normalizeDateField(laObject, "endDate");
        normalizeDateField(laObject, "ebdDate", "endDate");

        JsonNode student = laObject.get("student");
        if (student != null && student.isObject()) {
            normalizeDateField((ObjectNode) student, "birthDate");
        }
    }

    private void normalizeDateField(ObjectNode obj, String field) {
        normalizeDateField(obj, field, field);
    }

    private void normalizeDateField(ObjectNode obj, String fromField, String toField) {
        JsonNode value = obj.get(fromField);
        if (value != null && value.isTextual()) {
            String text = value.asText();
            int t = text.indexOf('T');
            if (t > 0) {
                text = text.substring(0, t);
            }
            obj.put(toField, text);
            if (!fromField.equals(toField)) {
                obj.remove(fromField);
            }
        }
    }

    private void normalizeComponents(JsonNode listOfComponentsNode) {
        if (listOfComponentsNode == null || !listOfComponentsNode.isObject()) {
            return;
        }

        ObjectNode listObj = (ObjectNode) listOfComponentsNode;
        wrapComponentListIfArray(listObj, "componentsStudied");
        wrapComponentListIfArray(listObj, "componentsRecognized");
        wrapComponentListIfArray(listObj, "virtualComponents");
        wrapComponentListIfArray(listObj, "blendedMobilityComponents");
        wrapComponentListIfArray(listObj, "shortTermDoctoralComponents");
    }

    private void wrapComponentListIfArray(ObjectNode parent, String fieldName) {
        JsonNode value = parent.get(fieldName);
        if (value != null && value.isArray()) {
            ObjectNode wrapper = parent.objectNode();
            wrapper.set("component", value);
            parent.set(fieldName, wrapper);
            value = wrapper;
        }
        if (value != null && value.isObject()) {
            JsonNode components = ((ObjectNode) value).get("component");
            if (components != null && components.isArray()) {
                normalizeComponentCredits(components);
            }
        }
    }

    private void normalizeComponentCredits(JsonNode componentsArray) {
        for (JsonNode compNode : componentsArray) {
            if (compNode != null && compNode.isObject()) {
                ObjectNode compObj = (ObjectNode) compNode;
                JsonNode credit = compObj.get("credit");
                if (credit != null && credit.isObject()) {
                    compObj.set("credit", compObj.arrayNode().add(credit));
                }
            }
        }
    }

    /*private javax.ws.rs.core.Response omobilityLasIndex(List<String> sendingHeiIds, List<String> receivingHeiIdList, List<String> receiving_academic_year_ids, List<String> globalIds, List<String> mobilityTypes, List<String> modifiedSinces) {
        String receiving_academic_year_id;
        String globalId;
        String mobilityType;
        String modifiedSince;

        Collection<String> heisCoveredByCertificate;
        if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
            heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
        } else {
            heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
        }

        if (heisCoveredByCertificate.isEmpty()) {
            return javax.ws.rs.core.Response.ok(new OmobilityLasIndexResponse()).build();
        }

        if (sendingHeiIds.size() != 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        }
        String sendingHeiId = sendingHeiIds.get(0);

        Map<String, String> urls = registryClient.getOmobilityLasHeiUrls(sendingHeiId);
        if (urls == null || urls.isEmpty()) {
            throw new EwpWebApplicationException("Unknown heiId: " + sendingHeiId, Response.Status.BAD_REQUEST);
        }


        if (receiving_academic_year_ids.size() > 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        } else if (!receiving_academic_year_ids.isEmpty()) {
            receiving_academic_year_id = receiving_academic_year_ids.get(0);
            try {
                //String adjustedDateString = receiving_academic_year_id.replaceAll("([\\+\\-]\\d{2}):(\\d{2})", "$1$2");
                System.out.println((new SimpleDateFormat("yyyy/yyyy")).parse(receiving_academic_year_id));
            } catch (ParseException e) {
                throw new EwpWebApplicationException("Can not convert date.", Response.Status.BAD_REQUEST);
            }
        } else {
            receiving_academic_year_id = null;
        }

        if (globalIds.size() > 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        } else if (!globalIds.isEmpty()) {
            globalId = globalIds.get(0);
        } else {
            globalId = null;
        }

        if (mobilityTypes.size() > 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        } else if (!mobilityTypes.isEmpty()) {
            mobilityType = mobilityTypes.get(0);
        } else {
            mobilityType = null;
        }

        if (modifiedSinces.size() > 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        } else if (!modifiedSinces.isEmpty()) {
            modifiedSince = modifiedSinces.get(0);
        } else {
            modifiedSince = null;
        }

        OmobilityLasIndexResponse response = new OmobilityLasIndexResponse();

        List<OlearningAgreement> mobilityList = learningAgreementEJB.findBySendingHeiId(sendingHeiId);

        if (receiving_academic_year_id != null && !receiving_academic_year_id.isEmpty()) {
            mobilityList = mobilityList.stream().filter(omobility -> anyMatchReceivingAcademicYear.test(omobility, receiving_academic_year_id)).collect(Collectors.toList());
        }

        if (globalId != null && !globalId.isEmpty()) {
            mobilityList = mobilityList.stream().filter(omobility -> anyMatchSpecifiedStudent.test(omobility, globalId)).collect(Collectors.toList());
        }

        if (mobilityType != null && !mobilityList.isEmpty()) {
            mobilityList = mobilityList.stream().filter(omobility -> anyMatchSpecifiedType.test(omobility, mobilityType)).collect(Collectors.toList());
        }

        if (modifiedSince != null && !modifiedSince.isEmpty()) {

            //SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");//2004-02-12T15:19:21+01:00
            Calendar calendarModifySince = Calendar.getInstance();

            try {
                OffsetDateTime dateTime = OffsetDateTime.parse(modifiedSince, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                calendarModifySince.setTime(Date.from(dateTime.toInstant()));
            } catch (Exception e) {
                throw new EwpWebApplicationException("Can not convert date.", Response.Status.BAD_REQUEST);
            }

            Date modified_since = calendarModifySince.getTime();

            LOG.info("modified_since: " + modified_since);

            List<OlearningAgreement> mobilities = new ArrayList<>();
            mobilityList.stream().forEachOrdered((m) -> {
                if (m != null) {
                    if (m.getModificateSince() != null) {
                        LOG.info("getModificateSince: " + m.getModificateSince());
                        if (m.getModificateSince().after(modified_since)) {
                            mobilities.add(m);
                        }
                    }
                }
            });


            mobilityList.clear();
            mobilityList.addAll(mobilities);
        }

        response.getOmobilityId().addAll(omobilityLasIds(mobilityList, receivingHeiIdList));
        //}

        return javax.ws.rs.core.Response.ok(response).build();
    }*/

    private javax.ws.rs.core.Response omobilityLasIndexAlgoria(List<String> sendingHeiIds, List<String> receivingHeiIdList, List<String> receiving_academic_year_ids, List<String> globalIds, List<String> mobilityTypes, List<String> modifiedSinces) {
        LOG.info("omobilityLasIndexAlgoria: Starting index request with parameters: sendingHeiIds=" + sendingHeiIds);
        String receiving_academic_year_id;
        String globalId;
        String mobilityType;
        String modifiedSince;

        Collection<String> heisCoveredByCertificate;
        if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
            heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
        } else {
            heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
        }

        if (heisCoveredByCertificate.isEmpty()) {
            return javax.ws.rs.core.Response.ok(new OmobilityLasIndexResponse()).build();
        }

        String recivingHeiId = heisCoveredByCertificate.iterator().next();

        if (sendingHeiIds.size() != 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        }

        /*Map<String, String> urls = registryClient.getOmobilityLasHeiUrls(recivingHeiId);
        if (urls == null || urls.isEmpty()) {
            throw new EwpWebApplicationException("Unknown heiId: " + recivingHeiId, Response.Status.BAD_REQUEST);
        }*/


        String fistYear = null;
        if (receiving_academic_year_ids.size() > 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        } else if (!receiving_academic_year_ids.isEmpty()) {
            receiving_academic_year_id = receiving_academic_year_ids.get(0);
            try {
                //String adjustedDateString = receiving_academic_year_id.replaceAll("([\\+\\-]\\d{2}):(\\d{2})", "$1$2");
                System.out.println((new SimpleDateFormat("yyyy/yyyy")).parse(receiving_academic_year_id));
                fistYear = receiving_academic_year_id.split("/")[0];
            } catch (ParseException e) {
                throw new EwpWebApplicationException("Can not convert date.", Response.Status.BAD_REQUEST);
            }
        } else {
            receiving_academic_year_id = null;
        }

        if (globalIds.size() > 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        } else if (!globalIds.isEmpty()) {
            globalId = globalIds.get(0);
        } else {
            globalId = null;
        }

        if (mobilityTypes.size() > 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        } else if (!mobilityTypes.isEmpty()) {
            mobilityType = mobilityTypes.get(0);
        } else {
            mobilityType = null;
        }

        if (modifiedSinces.size() > 1) {
            throw new EwpWebApplicationException("Missing argumanets for indexes.", Response.Status.BAD_REQUEST);
        } else if (!modifiedSinces.isEmpty()) {
            modifiedSince = modifiedSinces.get(0);
            OffsetDateTime dateTime;
            try {
                dateTime = OffsetDateTime.parse(modifiedSince, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException ex) {
                String normalized = modifiedSince.trim();
                // If offset is space-separated or missing sign, normalize to "+HH:MM"
                if (normalized.length() > 19) {
                    char c = normalized.charAt(19);
                    if (c == ' ') {
                        normalized = normalized.substring(0, 19) + "+" + normalized.substring(20);
                    } else if (c >= '0' && c <= '9') {
                        normalized = normalized.substring(0, 19) + "+" + normalized.substring(19);
                    }
                }
                dateTime = OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            }

            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

            modifiedSince = dateTime.format(formatter);
        } else {
            modifiedSince = null;
        }

        LOG.info("omobilityLasIndexAlgoria: Parameters parsed");

        OmobilityLasIndexResponse response = new OmobilityLasIndexResponse();

        String url = properties.getAlgoriaOmobilityLasUrl(recivingHeiId);
        String token = properties.getAlgoriaAuthotizationToken();

        WebTarget target = ClientBuilder.newBuilder().build().target(url.trim());

        if(fistYear != null) {
            target = target.queryParam("receiving_academic_year", fistYear);
        }
        if (globalId != null) {
            target = target.queryParam("student_id", globalId);
        }
        if (mobilityType != null) {
            target = target.queryParam("mobility_type", mobilityType);
        }
        if (modifiedSince != null) {
            target = target.queryParam("modified_since", modifiedSince); //TODO: check date format
        }

        Response algoriaResponse = target.request().header("Authorization", token).get();
        String rawBody = algoriaResponse.readEntity(String.class);
        try {
            ObjectMapper mapper = new ObjectMapper();
            AlgoriaOmobilityLasIndexDto dto = mapper.readValue(rawBody, AlgoriaOmobilityLasIndexDto.class);

            if (dto.getElements() != null) {
                response.getOmobilityId().addAll(dto.getElements());
            }
        } catch (Exception e) {
            LOG.warning("Algoria response (" + algoriaResponse.getStatus() + ") raw:\n" + rawBody);
            LOG.warning("Algoria response parse error: " + e.getMessage());
        }

        return javax.ws.rs.core.Response.ok(response).build();
    }

    /*private List<LearningAgreement> omobilitiesLas(List<OlearningAgreement> omobilityLasList, List<String> omobilityLasIdList) {
        List<LearningAgreement> omobilitiesLas = new ArrayList<>();
        omobilityLasList.stream().forEachOrdered((m) -> {
            if (omobilityLasIdList.contains(m.getId())) {
                omobilitiesLas.add(converter.convertToLearningAgreements(m));
            }
        });

        return omobilitiesLas;
    }*/

    /*private List<String> omobilityLasIds(List<OlearningAgreement> lasList, List<String> receivingHeiIdList) {
        List<String> omobilityLasIds = new ArrayList<>();

        lasList.stream().forEachOrdered((m) -> {
            if (receivingHeiIdList.isEmpty() || receivingHeiIdList.contains(m.getReceivingHei().getHeiId())) {
                omobilityLasIds.add(m.getId());
            }
        });

        return omobilityLasIds;
    }*/

    /*BiPredicate<OlearningAgreement, String> anyMatchReceivingAcademicYear = new BiPredicate<OlearningAgreement, String>() {
        @Override
        public boolean test(OlearningAgreement omobility, String receiving_academic_year_id) {
            return receiving_academic_year_id.equals(omobility.getReceivingAcademicTermEwpId());
        }
    };

    BiPredicate<OlearningAgreement, String> anyMatchSpecifiedStudent = new BiPredicate<OlearningAgreement, String>() {
        @Override
        public boolean test(OlearningAgreement omobility, String global_id) {
            if (omobility.getChangesProposal() == null || omobility.getChangesProposal().getStudent() == null) {
                return false;
            }

            return global_id.equals(omobility.getChangesProposal().getStudent().getGlobalId());
        }
    };

    BiPredicate<OlearningAgreement, String> anyMatchSpecifiedType = new BiPredicate<OlearningAgreement, String>() {
        @Override
        public boolean test(OlearningAgreement omobility, String mobilityType) {

            if (MobilityType.BLENDED.value().equals(mobilityType)) {
                return (omobility.getFirstVersion() != null && omobility.getFirstVersion().getBlendedMobilityComponents() != null && !omobility.getFirstVersion().getBlendedMobilityComponents().isEmpty())
                        || (omobility.getApprovedChanges() != null && omobility.getApprovedChanges().getBlendedMobilityComponents() != null && !omobility.getApprovedChanges().getBlendedMobilityComponents().isEmpty())
                        || (omobility.getChangesProposal() != null && omobility.getChangesProposal().getBlendedMobilityComponents() != null && !omobility.getChangesProposal().getBlendedMobilityComponents().isEmpty());
            } else if (MobilityType.DOCTORAL.value().equals(mobilityType)) {
                return (omobility.getFirstVersion() != null && omobility.getFirstVersion().getShortTermDoctoralComponents() != null && !omobility.getFirstVersion().getShortTermDoctoralComponents().isEmpty())
                        || (omobility.getApprovedChanges() != null && omobility.getApprovedChanges().getShortTermDoctoralComponents() != null && !omobility.getApprovedChanges().getShortTermDoctoralComponents().isEmpty())
                        || (omobility.getChangesProposal() != null && omobility.getChangesProposal().getShortTermDoctoralComponents() != null && !omobility.getChangesProposal().getShortTermDoctoralComponents().isEmpty());
            } else if (MobilityType.SEMESTRE.value().equals(mobilityType)) {
                return (omobility.getFirstVersion() != null && omobility.getFirstVersion().getComponentsStudied() != null && !omobility.getFirstVersion().getComponentsStudied().isEmpty())
                        || (omobility.getApprovedChanges() != null && omobility.getApprovedChanges().getComponentsStudied() != null && !omobility.getApprovedChanges().getComponentsStudied().isEmpty())
                        || (omobility.getChangesProposal() != null && omobility.getChangesProposal().getComponentsStudied() != null && !omobility.getChangesProposal().getComponentsStudied().isEmpty());
            }
            return false;
        }
    };*/
}
