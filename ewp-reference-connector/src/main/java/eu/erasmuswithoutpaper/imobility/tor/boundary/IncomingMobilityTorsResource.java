package eu.erasmuswithoutpaper.imobility.tor.boundary;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.imobilities.Imobilities;
import eu.erasmuswithoutpaper.api.imobilities.endpoints.ImobilitiesGetResponse;
import eu.erasmuswithoutpaper.api.imobilities.endpoints.StudentMobility;
import eu.erasmuswithoutpaper.api.imobilities.tors.endpoints.ImobilityTorsGetResponse;
import eu.erasmuswithoutpaper.api.imobilities.tors.endpoints.ImobilityTorsIndexResponse;
import eu.erasmuswithoutpaper.api.omobilities.endpoints.OmobilitiesIndexResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasIndexResponse;
import eu.erasmuswithoutpaper.api.omobilities.stats.OmobilityStatsResponse;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;
import eu.erasmuswithoutpaper.common.control.RegistryClient;
import eu.erasmuswithoutpaper.error.control.EwpWebApplicationException;
import eu.erasmuswithoutpaper.omobility.dto.AlgoriaOmobilityIndexDto;
import eu.erasmuswithoutpaper.security.EwpAuthenticate;
import eu.erasmuswithoutpaper.security.InternalAuthenticate;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Stateless
@Path("imobilities/tors")
public class IncomingMobilityTorsResource {
    @Inject
    GlobalProperties properties;

    @Inject
    RegistryClient registryClient;

    @Context
    HttpServletRequest httpRequest;

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(IncomingMobilityTorsResource.class.getCanonicalName());


    @GET
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public Response torGetGet(@QueryParam("omobility_id") List<String> omobilityIds) {
        return torGetAlgoria(omobilityIds);
    }
    
    @POST
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public Response torGetPost(@FormParam("omobility_id") List<String> omobilityIds) {
        return torGetAlgoria(omobilityIds);
    }

    @GET
    @Path("get_test")
    @Produces(MediaType.APPLICATION_XML)
    @InternalAuthenticate
    public Response torGetTest(@QueryParam("omobility_id") List<String> omobilityIds, @QueryParam("hei_id") String heiId) {
        return torGetAlgoria(heiId, omobilityIds);
    }

    @GET
    @Path("index")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public Response torIndexGet(@QueryParam("modified_since") List<String> modifiedSinces) {
        return torIndexAlgoria(modifiedSinces);
    }

    @POST
    @Path("index")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public Response torIndexPost(@QueryParam("modified_since") List<String> modifiedSinces) {
        return torIndexAlgoria(modifiedSinces);
    }

    @GET
    @Path("index_test")
    @Produces(MediaType.APPLICATION_XML)
    @InternalAuthenticate
    public Response torIndexTest(@QueryParam("modified_since") List<String> modifiedSinces, @QueryParam("hei_id") String heiId) {
        return torIndexAlgoria(heiId, modifiedSinces);
    }
    
    @POST
    @Path("cnr")
    @Produces(MediaType.APPLICATION_XML)
    public Response cnrPost(@FormParam("omobility_id") List<String> omobilityIds) {

        if (omobilityIds.size() > properties.getMaxMobilityIds()) {
            throw new EwpWebApplicationException("Max number of omobility id's has exceeded.", Response.Status.BAD_REQUEST);
        }

        Collection<String> heisCoveredByCertificate;
        if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
            heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
        } else {
            heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
        }

        if (heisCoveredByCertificate.isEmpty()) {
            return Response.ok(new OmobilityLasIndexResponse()).build();
        }

        String heiId = heisCoveredByCertificate.iterator().next();

        CompletableFuture.runAsync(() -> {
            for (String omobilityId : omobilityIds) {
                try {
                    notifyAlgoriaImobility(heiId, omobilityId);
                } catch (Exception e) {
                    LOG.fine("Error in AuxIiaApprovalThread: " + e.getMessage());
                }
            }
        });


        eu.erasmuswithoutpaper.api.omobilities.las.cnr.endpoints.ObjectFactory factory = new eu.erasmuswithoutpaper.api.omobilities.las.cnr.endpoints.ObjectFactory();

        return Response.ok(factory.createOmobilityLaCnrResponse(new Empty())).build();
    }

    @GET
    @Path("stats")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response omobilityGetStatsAlgoria() {
        LOG.info("---- START /imobilities/tors/stats ----");

        String url = properties.getAlgoriaImobilityTorStatsUrl();
        String token = properties.getAlgoriaAuthotizationToken();
        LOG.info("Algoria stats outbound url=" + url);

        Response algoriaResponse = ClientBuilder.newBuilder().build().target(url.trim()).request().header("Authorization", token).get();
        String rawBody = algoriaResponse.readEntity(String.class);
        try {
            LOG.info("Algoria stats response status=" + algoriaResponse.getStatus());
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
            /*if (statsNode != null && statsNode.isArray()) {
                for (JsonNode statNode : statsNode) {
                    if (statNode.isObject()) {
                        ObjectNode statObject = (ObjectNode) statNode;
                        JsonNode yearNode = statNode.get("receivingAcademicYearId");
                        if (yearNode != null && yearNode.isTextual()) {
                            statObject.put("receivingAcademicYearId", normalizeAcademicYearId(yearNode.asText()));
                        }

                        BigInteger someVersionApproved = readBigInteger(statNode.get("laIncomingSomeVersionApproved"));
                        if (someVersionApproved != null) {
                            statObject.put("laIncomingSomeVersionApproved", someVersionApproved.toString());
                        }
                    }
                }
            }*/

            OmobilityStatsResponse response = mapper.convertValue(root, OmobilityStatsResponse.class);
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

    private Response torGetAlgoria(List<String> omobilityIds) {
        Collection<String> heisCoveredByCertificate;
        if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
            heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
        } else {
            heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
        }

        if (heisCoveredByCertificate.isEmpty()) {
            throw new EwpWebApplicationException("No HEIs covered by this certificate.", Response.Status.FORBIDDEN);
        }

        String heiId = heisCoveredByCertificate.iterator().next();

        return torGetAlgoria(heiId, omobilityIds);
    }
    
    private Response torGetAlgoria(String heiId, List<String> omobilityIds) {
        LOG.fine("heiId: " + heiId);

        if (omobilityIds.size() > properties.getMaxMobilityIds()) {
            throw new EwpWebApplicationException("Max number of omobility id's has exceeded.", Response.Status.BAD_REQUEST);
        }

        LOG.fine("omobilityIds: " + omobilityIds.toString());

        ImobilityTorsGetResponse response = new ImobilityTorsGetResponse();
        String token = properties.getAlgoriaAuthotizationToken();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        for (String omobilityId : omobilityIds) {
            String url = properties.getAlgoriaImobilityTorByIDUrl(heiId, omobilityId);
            LOG.fine("Algoria GET URL: " + url);
            WebTarget target = ClientBuilder.newBuilder().build().target(url.trim());
            Response algoriaResponse = target.request().header("Authorization", token).get();
            String rawBody = algoriaResponse.readEntity(String.class);
            try {
                JsonNode root = mapper.readTree(rawBody);

                JsonNode laNode = root.get("tor");
                if (laNode != null && laNode.isObject()) {
                    ObjectNode laObject = (ObjectNode) laNode;

                    ImobilityTorsGetResponse.Tor tor = mapper.treeToValue(laObject, ImobilityTorsGetResponse.Tor.class);
                    response.getTor().add(tor);
                }
            } catch (Exception e) {
                LOG.warning("Algoria get response (" + algoriaResponse.getStatus() + ") for " + omobilityIds + " raw:\n" + rawBody);
                LOG.warning("Algoria get parse error for " + omobilityIds + ": " + e.getMessage());
            } finally {
                algoriaResponse.close();
            }
        }

        return Response.ok(response).build();
    }

    private javax.ws.rs.core.Response torIndexAlgoria(List<String> modifiedSinces) {
        Collection<String> heisCoveredByCertificate;
        if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
            heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
        } else {
            heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
        }

        if (heisCoveredByCertificate.isEmpty()) {
            throw new EwpWebApplicationException("No HEIs covered by this certificate.", Response.Status.FORBIDDEN);
        }

        String partnerHeiId = heisCoveredByCertificate.iterator().next();

        return torIndexAlgoria(partnerHeiId, modifiedSinces);
    }

    private javax.ws.rs.core.Response torIndexAlgoria(String partnerHeiId, List<String> modifiedSinces) {
        LOG.info("torIndex: Starting index request with parameters: partnerHeiId=" + partnerHeiId);
        String modifiedSince;


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

        LOG.info("torIndex: Parameters parsed");

        ImobilityTorsIndexResponse response = new ImobilityTorsIndexResponse();

        String url = properties.getAlgoriaImobilityTorUrl(partnerHeiId);
        String token = properties.getAlgoriaAuthotizationToken();

        WebTarget target = ClientBuilder.newBuilder().build().target(url.trim());

        if (modifiedSince != null) {
            target = target.queryParam("modified_since", modifiedSince);
        }

        Response algoriaResponse = target.request().header("Authorization", token).get();
        String rawBody = algoriaResponse.readEntity(String.class);
        try {
            ObjectMapper mapper = new ObjectMapper();
            AlgoriaOmobilityIndexDto dto = mapper.readValue(rawBody, AlgoriaOmobilityIndexDto.class);

            if (dto.getElements() != null) {
                response.getOmobilityId().addAll(dto.getElements());
            }
        } catch (Exception e) {
            LOG.warning("Algoria response (" + algoriaResponse.getStatus() + ") raw:\n" + rawBody);
            LOG.warning("Algoria response parse error: " + e.getMessage());
        }

        return javax.ws.rs.core.Response.ok(response).build();
    }

    private void notifyAlgoriaImobility(String sendingHeiId, String omobilityId) {
        String token = properties.getAlgoriaAuthotizationToken();
        String url = properties.getAlgoriaImobilityTorNotifyUrl(sendingHeiId, omobilityId);
        try {
            Response algoriaResponse = ClientBuilder.newBuilder()
                    .build()
                    .target(url.trim())
                    .request(MediaType.APPLICATION_JSON_TYPE)
                    .header("Authorization", token)
                    .method("POST");
            try {
                String rawBody = algoriaResponse.readEntity(String.class);
                if (algoriaResponse.getStatus() < 200 || algoriaResponse.getStatus() >= 300) {
                    LOG.warning("Algoria notify failed. HTTP " + algoriaResponse.getStatus()
                            + " URL=" + url + " body:\n" + rawBody);
                } else {
                    LOG.fine("Algoria notify OK. HTTP " + algoriaResponse.getStatus()
                            + " URL=" + url + " body:\n" + rawBody);
                }
            } finally {
                algoriaResponse.close();
            }
        } catch (Exception e) {
            LOG.warning("Algoria notify error for imobilityId=" + omobilityId + ": " + e.getMessage());
        }
    }
}
