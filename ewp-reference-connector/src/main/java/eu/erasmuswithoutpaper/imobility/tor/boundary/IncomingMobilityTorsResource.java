package eu.erasmuswithoutpaper.imobility.tor.boundary;

import eu.emrex.elmo.Attachment;
import eu.emrex.elmo.Elmo;
import eu.emrex.elmo.Issuer;
import eu.emrex.elmo.LearningOpportunitySpecification;
import eu.emrex.elmo.TokenWithOptionalLang;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.imobilities.tors.endpoints.ImobilityTorsGetResponse;
import eu.erasmuswithoutpaper.api.imobilities.tors.endpoints.ImobilityTorsIndexResponse;
import eu.erasmuswithoutpaper.api.imobilities.tors.stats.ImobilityTorStatsResponse;
import eu.erasmuswithoutpaper.api.omobilities.las.endpoints.OmobilityLasIndexResponse;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;
import eu.erasmuswithoutpaper.common.control.RegistryClient;
import eu.erasmuswithoutpaper.error.control.EwpWebApplicationException;
import eu.erasmuswithoutpaper.imobility.tor.dto.AlgoriaImobilityTorGetDto;
import eu.erasmuswithoutpaper.imobility.tor.dto.AlgoriaImobilityTorIndexDto;
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
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
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
    //@EwpAuthenticate
    public javax.ws.rs.core.Response imobilityGetStatsAlgoria() {
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
            JsonNode statsNode = root.get("academicYearTorStats");
            ImobilityTorStatsResponse response = new ImobilityTorStatsResponse();
            if (statsNode != null && statsNode.isArray()) {
                for (JsonNode statNode : statsNode) {
                    if (statNode.isObject()) {
                        ImobilityTorStatsResponse.AcademicYearStats academicYearStats =
                                new ImobilityTorStatsResponse.AcademicYearStats();
                        academicYearStats.setReceivingAcademicYearId(
                                normalizeAcademicYearId(readText(statNode.get("receivingAcademicYearId"))));
                        academicYearStats.setImobilityTorTotal(readBigInteger(statNode.get("imobilityTorTotal")));
                        response.getAcademicYearStats().add(academicYearStats);
                    }
                }
            }

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
                if (algoriaResponse.getStatus() < 200 || algoriaResponse.getStatus() >= 300) {
                    throw new EwpWebApplicationException("TOR get request failed. HTTP " + algoriaResponse.getStatus(),
                            Response.Status.BAD_GATEWAY);
                }

                AlgoriaImobilityTorGetDto dto = mapper.readValue(rawBody, AlgoriaImobilityTorGetDto.class);
                if (dto.getTor() != null) {
                    ImobilityTorsGetResponse.Tor tor = toTorResponse(dto.getTor(), omobilityId);
                    response.getTor().add(tor);
                }
            } catch (EwpWebApplicationException e) {
                LOG.warning("Algoria TOR get failed with known error: " + e.getMessage());
                throw e;
            } catch (Exception e) {
                LOG.warning("Algoria get response (" + algoriaResponse.getStatus() + ") for " + omobilityIds + " raw:\n" + rawBody);
                LOG.warning("Algoria get parse error for " + omobilityIds + ": " + e.getMessage());
                throw new EwpWebApplicationException("TOR get request failed", Response.Status.BAD_GATEWAY);
            } finally {
                algoriaResponse.close();
            }
        }

        return Response.ok(response).build();
    }

    private ImobilityTorsGetResponse.Tor toTorResponse(AlgoriaImobilityTorGetDto.Tor source, String fallbackOmobilityId)
            throws DatatypeConfigurationException {
        ImobilityTorsGetResponse.Tor tor = new ImobilityTorsGetResponse.Tor();
        tor.setOmobilityId(firstNonBlank(source.getOmobilityId(), fallbackOmobilityId));
        tor.setReceivingAcademicYearId(firstNonBlank(source.getReceivingAcademicYearId(),
                academicYearFrom(source.getTorAvailableSince())));
        tor.setElmo(toElmo(source));
        return tor;
    }

    private Elmo toElmo(AlgoriaImobilityTorGetDto.Tor source) throws DatatypeConfigurationException {
        XMLGregorianCalendar issueDate = toXmlDateTime(source.getTorAvailableSince());

        Elmo elmo = new Elmo();
        elmo.setGeneratedDate(issueDate);
        elmo.setLearner(toLearner(source.getLearner()));

        Elmo.Report report = new Elmo.Report();
        report.setIssuer(toIssuer(source.getIssuer()));
        report.setIssueDate(issueDate);

        if (source.getLearningOutcomes() != null) {
            for (AlgoriaImobilityTorGetDto.LearningOutcome learningOutcome : source.getLearningOutcomes()) {
                report.getLearningOpportunitySpecification().add(toLearningOpportunitySpecification(learningOutcome));
            }
        }

        if (!isBlank(source.getAttachment())) {
            report.getAttachment().add(toAttachment(source.getAttachment()));
        }

        elmo.getReport().add(report);
        return elmo;
    }

    private Elmo.Learner toLearner(AlgoriaImobilityTorGetDto.Learner source) throws DatatypeConfigurationException {
        Elmo.Learner learner = new Elmo.Learner();
        if (source == null) {
            return learner;
        }

        learner.setGivenNames(source.getGivenNames());
        learner.setFamilyName(source.getFamilyName());
        if (!isBlank(source.getBirthDate())) {
            learner.setDateOfBirth(toXmlDate(source.getBirthDate()));
        }
        if (!isBlank(source.getGlobalId())) {
            Elmo.Learner.Identifier identifier = new Elmo.Learner.Identifier();
            identifier.setType("esi");
            identifier.setValue(source.getGlobalId());
            learner.getIdentifier().add(identifier);
        }
        return learner;
    }

    private Issuer toIssuer(AlgoriaImobilityTorGetDto.Issuer source) {
        Issuer issuer = new Issuer();
        if (source == null) {
            return issuer;
        }

        if (!isBlank(source.getCountry())) {
            Issuer.Country country = new Issuer.Country();
            country.setType("iso-3166-1-alpha-2");
            country.setValue(source.getCountry());
            issuer.setCountry(country);
        }
        addIssuerIdentifier(issuer, "schac", source.getHeiId());
        addIssuerIdentifier(issuer, "erasmus", source.getErasmusCode());
        addIssuerIdentifier(issuer, "pic", source.getPic());
        if (!isBlank(source.getName())) {
            issuer.getTitle().add(token(source.getName(), null));
        }
        issuer.setUrl(source.getUrl());
        return issuer;
    }

    private LearningOpportunitySpecification toLearningOpportunitySpecification(
            AlgoriaImobilityTorGetDto.LearningOutcome source) {
        LearningOpportunitySpecification specification = new LearningOpportunitySpecification();
        addLosIdentifier(specification, "ewp-los-id", source.getLosId());
        addLosIdentifier(specification, "local-code", source.getLosCode());
        if (!isBlank(source.getTitle())) {
            specification.getTitle().add(token(source.getTitle(), null));
        }
        if (!isBlank(source.getTitleEn())) {
            specification.getTitle().add(token(source.getTitleEn(), "en"));
        }
        specification.setType("Course");

        LearningOpportunitySpecification.Specifies specifies = new LearningOpportunitySpecification.Specifies();
        LearningOpportunitySpecification.Specifies.LearningOpportunityInstance instance =
                new LearningOpportunitySpecification.Specifies.LearningOpportunityInstance();
        addLoiIdentifier(instance, "ewp-loi-id", source.getLoiId());
        addLoiIdentifier(instance, "local-code", source.getLoiCode());
        instance.setStatus("passed");
        instance.setResultLabel(firstNonBlank(source.getAnnouncement(),
                source.getGradeNumeric() == null ? null : source.getGradeNumeric().toPlainString()));
        if (source.getCredits() != null) {
            LearningOpportunitySpecification.Specifies.LearningOpportunityInstance.Credit credit =
                    new LearningOpportunitySpecification.Specifies.LearningOpportunityInstance.Credit();
            credit.setScheme("ECTS");
            credit.setValue(source.getCredits());
            instance.getCredit().add(credit);
        }

        specifies.setLearningOpportunityInstance(instance);
        specification.setSpecifies(specifies);
        return specification;
    }

    private Attachment toAttachment(String content) {
        Attachment attachment = new Attachment();
        attachment.setType("Transcript of Records");
        attachment.getTitle().add(token("Transcript of Records", "en"));
        attachment.getContent().add(token(content, null));
        return attachment;
    }

    private void addIssuerIdentifier(Issuer issuer, String type, String value) {
        if (isBlank(value)) {
            return;
        }
        Issuer.Identifier identifier = new Issuer.Identifier();
        identifier.setType(type);
        identifier.setValue(value);
        issuer.getIdentifier().add(identifier);
    }

    private void addLosIdentifier(LearningOpportunitySpecification specification, String type, String value) {
        if (isBlank(value)) {
            return;
        }
        LearningOpportunitySpecification.Identifier identifier = new LearningOpportunitySpecification.Identifier();
        identifier.setType(type);
        identifier.setValue(value);
        specification.getIdentifier().add(identifier);
    }

    private void addLoiIdentifier(LearningOpportunitySpecification.Specifies.LearningOpportunityInstance instance,
                                  String type, String value) {
        if (isBlank(value)) {
            return;
        }
        LearningOpportunitySpecification.Specifies.LearningOpportunityInstance.Identifier identifier =
                new LearningOpportunitySpecification.Specifies.LearningOpportunityInstance.Identifier();
        identifier.setType(type);
        identifier.setValue(value);
        instance.getIdentifier().add(identifier);
    }

    private TokenWithOptionalLang token(String value, String lang) {
        TokenWithOptionalLang token = new TokenWithOptionalLang();
        token.setValue(value);
        token.setLang(lang);
        return token;
    }

    private XMLGregorianCalendar toXmlDateTime(String value) throws DatatypeConfigurationException {
        String dateTime = firstNonBlank(value, OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(dateTime);
    }

    private XMLGregorianCalendar toXmlDate(String value) throws DatatypeConfigurationException {
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(value);
    }

    private String academicYearFrom(String dateTimeValue) {
        OffsetDateTime dateTime;
        try {
            dateTime = OffsetDateTime.parse(dateTimeValue, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            dateTime = OffsetDateTime.now();
        }

        int startYear = dateTime.getMonthValue() >= 8 ? dateTime.getYear() : dateTime.getYear() - 1;
        return startYear + "/" + (startYear + 1);
    }

    private String normalizeAcademicYearId(String value) {
        if (isBlank(value)) {
            return value;
        }

        String trimmed = value.trim();
        if (trimmed.matches("\\d{4}/\\d{4}")) {
            return trimmed;
        }
        if (trimmed.matches("\\d{4}/\\d{2}")) {
            String[] parts = trimmed.split("/");
            String century = parts[0].substring(0, 2);
            return parts[0] + "/" + century + parts[1];
        }
        return trimmed;
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String readText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private BigInteger readBigInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigInteger.ZERO;
        }
        if (node.isNumber()) {
            return node.bigIntegerValue();
        }
        String value = node.asText();
        return isBlank(value) ? BigInteger.ZERO : new BigInteger(value);
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
            if (algoriaResponse.getStatus() < 200 || algoriaResponse.getStatus() >= 300) {
                throw new EwpWebApplicationException("TOR index request failed. HTTP " + algoriaResponse.getStatus(),
                        Response.Status.BAD_GATEWAY);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            AlgoriaImobilityTorIndexDto dto = mapper.readValue(rawBody, AlgoriaImobilityTorIndexDto.class);

            if (dto.getElements() != null) {
                response.getOmobilityId().addAll(dto.getElements());
            }
        } catch (EwpWebApplicationException e) {
            LOG.warning("Algoria TOR index failed with known error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.warning("Algoria response (" + algoriaResponse.getStatus() + ") raw:\n" + rawBody);
            LOG.warning("Algoria response parse error: " + e.getMessage());
            throw new EwpWebApplicationException("TOR index request failed", Response.Status.BAD_GATEWAY);
        } finally {
            algoriaResponse.close();
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
