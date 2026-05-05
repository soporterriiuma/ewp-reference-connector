package eu.erasmuswithoutpaper.imobility.boundary;

import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.imobilities.endpoints.ImobilitiesGetResponse;
import eu.erasmuswithoutpaper.api.imobilities.endpoints.StudentMobility;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasIndexResponse;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;
import eu.erasmuswithoutpaper.common.control.RegistryClient;
import eu.erasmuswithoutpaper.error.control.EwpWebApplicationException;
import eu.erasmuswithoutpaper.security.EwpAuthenticate;
import eu.erasmuswithoutpaper.security.InternalAuthenticate;

@Stateless
@Path("imobilities")
public class IncomingMobilityResource {
    @Inject
    GlobalProperties properties;

    @Inject
    RegistryClient registryClient;

    @Context
    HttpServletRequest httpRequest;

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(IncomingMobilityResource.class.getCanonicalName());


    @GET
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response mobilityGetGet(@QueryParam("omobility_id") List<String> omobilityIds) {
        return mobilityGetAlgoria(omobilityIds);
    }
    
    @POST
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response mobilityGetPost(@FormParam("omobility_id") List<String> omobilityIds) {
        return mobilityGetAlgoria(omobilityIds);
    }

    @GET
    @Path("get_test")
    @Produces(MediaType.APPLICATION_XML)
    @InternalAuthenticate
    public javax.ws.rs.core.Response mobilityGetTest(@QueryParam("omobility_id") List<String> omobilityIds, @QueryParam("hei_id") String heiId) {
        LOG.info("---- START /imobilities/get_test ----");
        return mobilityGetAlgoria(heiId, omobilityIds);
    }
    
    @POST
    @Path("cnr")
    @Produces(MediaType.APPLICATION_XML)
    public javax.ws.rs.core.Response cnrPost(@FormParam("omobility_id") List<String> omobilityIds) {

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
            return javax.ws.rs.core.Response.ok(new OmobilityLasIndexResponse()).build();
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

        return javax.ws.rs.core.Response.ok(factory.createOmobilityLaCnrResponse(new Empty())).build();
    }

    private javax.ws.rs.core.Response mobilityGetAlgoria(List<String> omobilityIds) {
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

        return mobilityGetAlgoria(heiId, omobilityIds);
    }
    
    private javax.ws.rs.core.Response mobilityGetAlgoria(String heiId, List<String> omobilityIds) {
        LOG.fine("heiId: " + heiId);

        if (omobilityIds.size() > properties.getMaxMobilityIds()) {
            throw new EwpWebApplicationException("Max number of omobility id's has exceeded.", Response.Status.BAD_REQUEST);
        }

        LOG.fine("omobilityIds: " + omobilityIds.toString());

        ImobilitiesGetResponse response = new ImobilitiesGetResponse();
        String token = properties.getAlgoriaAuthotizationToken();
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        for (String omobilityId : omobilityIds) {
            String url = properties.getAlgoriaOmobilityByIDUrl(heiId, omobilityId);
            LOG.fine("Algoria GET URL: " + url);
            WebTarget target = ClientBuilder.newBuilder().build().target(url.trim());
            Response algoriaResponse = target.request().header("Authorization", token).get();
            String rawBody = algoriaResponse.readEntity(String.class);
            try {
                JsonNode root = mapper.readTree(rawBody);

                JsonNode laNode = root.get("la");
                if (laNode != null && laNode.isObject()) {
                    ObjectNode laObject = (ObjectNode) laNode;

                    StudentMobility la = mapper.treeToValue(laObject, StudentMobility.class);
                    response.getSingleIncomingMobilityObject().add(la);
                }
            } catch (Exception e) {
                LOG.warning("Algoria get response (" + algoriaResponse.getStatus() + ") for " + omobilityIds + " raw:\n" + rawBody);
                LOG.warning("Algoria get parse error for " + omobilityIds + ": " + e.getMessage());
            } finally {
                algoriaResponse.close();
            }
        }

        return javax.ws.rs.core.Response.ok(response).build();
    }

    private void notifyAlgoriaImobility(String sendingHeiId, String omobilityId) {
        String token = properties.getAlgoriaAuthotizationToken();
        String url = properties.getAlgoriaOmobilityNotifyUrl(sendingHeiId, omobilityId);
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
