package eu.erasmuswithoutpaper.iia.boundary;

import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.iias.cnr.ObjectFactory;
import eu.erasmuswithoutpaper.api.iias.endpoints.IiasGetResponse;
import eu.erasmuswithoutpaper.api.iias.endpoints.IiasIndexResponse;
import eu.erasmuswithoutpaper.api.iias.endpoints.IiasStatsResponse;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;
import eu.erasmuswithoutpaper.common.control.RegistryClient;
import eu.erasmuswithoutpaper.error.control.EwpWebApplicationException;
import eu.erasmuswithoutpaper.iia.approval.entity.IiaApproval;
import eu.erasmuswithoutpaper.iia.common.IiaTaskEnum;
import eu.erasmuswithoutpaper.iia.common.IiaTaskService;
import eu.erasmuswithoutpaper.iia.control.IiaConverter;
import eu.erasmuswithoutpaper.iia.control.IiasEJB;
import eu.erasmuswithoutpaper.iia.entity.CooperationCondition;
import eu.erasmuswithoutpaper.iia.entity.Iia;
import eu.erasmuswithoutpaper.notification.entity.Notification;
import eu.erasmuswithoutpaper.notification.entity.NotificationTypes;
import eu.erasmuswithoutpaper.security.EwpAuthenticate;

import java.util.logging.Level;
import java.util.stream.Stream;

@Stateless
@Path("iias")
public class IiaResource {

    private static final Logger LOG = Logger.getLogger(IiaResource.class.getCanonicalName());

    @EJB
    IiasEJB iiasEjb;

    @Inject
    GlobalProperties properties;

    @Inject
    IiaConverter iiaConverter;

    @Context
    HttpServletRequest httpRequest;

    @Inject
    AuxIiaThread ait;

    @Inject
    RegistryClient registryClient;

    @GET
    @Path("index")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response indexGet(@QueryParam("receiving_academic_year_id") List<String> receiving_academic_year_id, @QueryParam("modified_since") List<String> modified_since) {
        return iiaIndex(receiving_academic_year_id, modified_since);
    }

    @POST
    @Path("index")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response indexPost(@FormParam("receiving_academic_year_id") List<String> receiving_academic_year_id, @FormParam("modified_since") List<String> modified_since) {
        return iiaIndex(receiving_academic_year_id, modified_since);
    }

    private javax.ws.rs.core.Response iiaIndex(List<String> receiving_academic_year_id, List<String> modified_since) {

        Collection<String> heisCoveredByCertificate;
        if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
            heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
        } else {
            heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
        }

        if (heisCoveredByCertificate.isEmpty()) {
            throw new EwpWebApplicationException("No HEIs covered by this certificate.", Response.Status.FORBIDDEN);
        }

        String senderHeiId = heisCoveredByCertificate.iterator().next();

        if (modified_since != null && modified_since.size() > 1) {
            throw new EwpWebApplicationException("Not allow more than one value of modified_since", Response.Status.BAD_REQUEST);
        }

        //receiving_academic_year_id
        if (receiving_academic_year_id != null) {
            boolean match = true;
            Iterator<String> iterator = receiving_academic_year_id.iterator();
            while (iterator.hasNext() && match) {
                String yearId = (String) iterator.next();

                if (!yearId.matches("\\d{4}\\/\\d{4}")) {
                    match = false;
                }
            }

            if (!match) {
                throw new EwpWebApplicationException("receiving_academic_year_id is not in the correct format", Response.Status.BAD_REQUEST);
            }
        }

        IiasIndexResponse response = new IiasIndexResponse();
        List<Iia> filteredIiaList = iiasEjb.findAll();

        LOG.fine("Filtered:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

        if(!filteredIiaList.isEmpty()){
            filteredIiaList = filteredIiaList.stream().filter(iia ->
                iia.getCooperationConditions().stream().anyMatch(c ->
                    senderHeiId.equals(c.getReceivingPartner().getInstitutionId()) ||
                    senderHeiId.equals(c.getSendingPartner().getInstitutionId())
                )
            ).collect(Collectors.toList());
        }

        LOG.fine("Filtered 0:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

        if (!filteredIiaList.isEmpty()) {

            filteredIiaList = new ArrayList<>(filteredIiaList);

            List<Iia> filteredIiaByReceivingAcademic = new ArrayList<>();
            if (receiving_academic_year_id != null && !receiving_academic_year_id.isEmpty()) {

                for (String year_id : receiving_academic_year_id) {
                    List<Iia> filterefList = filteredIiaList.stream().filter(iia -> anyMatchBetweenReceivingAcademicYear.test(iia, year_id)).collect(Collectors.toList());

                    filteredIiaByReceivingAcademic.addAll(filterefList);
                }

                filteredIiaList = new ArrayList<Iia>(filteredIiaByReceivingAcademic);
            }

            LOG.fine("Filtered 1:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

            if (modified_since != null && !modified_since.isEmpty()) {

                Calendar calendarModifySince = Calendar.getInstance();

                List<Iia> tempFilteredModifiedSince = new ArrayList<>();

                String modifiedValue = modified_since.get(0);
                if (LOG.getLevel() != null) {
                    LOG.log(LOG.getLevel(), "\n\n\n" + modifiedValue + "\n\n\n");
                } else {
                    LOG.log(Level.FINE, "\n\n\n" + modifiedValue + "\n\n\n");
                }
                Date date = javax.xml.bind.DatatypeConverter.parseDateTime(modifiedValue).getTime();
                calendarModifySince.setTime(date);
                List<Iia> aux = filteredIiaList.stream().filter(iia -> compareModifiedSince.test(iia, calendarModifySince)).collect(Collectors.toList());
                if (aux != null) {
                    tempFilteredModifiedSince.addAll(aux);
                }

                filteredIiaList = new ArrayList<>(tempFilteredModifiedSince);

            }

            LOG.fine("Filtered 2:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));
        }

        LOG.fine("Filtered 3:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

        if(!filteredIiaList.isEmpty()){
            filteredIiaList = filteredIiaList.stream().filter(iia -> iia.getOriginal() == null).collect(Collectors.toList());
        }

        LOG.fine("Filtered INTERNAL:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

        if (!filteredIiaList.isEmpty()) {
            List<String> iiaIds = filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList());
            LOG.fine("IIA IDs:" + iiaIds);
            response.getIiaId().addAll(iiaIds);
        }

        return javax.ws.rs.core.Response.ok(response).build();
    }

    @GET
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response getGet(@QueryParam("iia_id") List<String> iiaIdList) {
        if (iiaIdList == null || iiaIdList.isEmpty()) {
            throw new EwpWebApplicationException("No iia_id provided", Response.Status.BAD_REQUEST);
        }

        if (iiaIdList.size() > properties.getMaxIiaIds()) {
            throw new EwpWebApplicationException("Max number of IIA ids has exceeded.", Response.Status.BAD_REQUEST);
        }

        try {
            Collection<String> heisCoveredByCertificate;
            if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
                heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
            } else {
                heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
            }

            if (!heisCoveredByCertificate.isEmpty()) {
                String notifierHeiId = heisCoveredByCertificate.iterator().next();

                LOG.fine("GET REQUEST FROM: " + notifierHeiId);
            }
        }catch (Exception e) {
            LOG.fine("ERROR OBTAINING HEI ID FROM CERTIFICATE");
        }

        return iiaGet(iiaIdList);

    }

    @POST
    @Path("get")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response getPost(@FormParam("iia_id") List<String> iiaIdList) {
        if (iiaIdList == null || iiaIdList.isEmpty()) {
            throw new EwpWebApplicationException("No iia_id provided", Response.Status.BAD_REQUEST);
        }

        if (iiaIdList.size() > properties.getMaxIiaIds()) {
            throw new EwpWebApplicationException("Max number of IIA ids has exceeded.", Response.Status.BAD_REQUEST);
        }

        return iiaGet(iiaIdList);
    }

    @GET
    @Path("index_json")
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response indexGetJson(@QueryParam("receiving_academic_year_id") List<String> receiving_academic_year_id, @QueryParam("modified_since") List<String> modified_since, @QueryParam("sender_hei_id") String senderHeiId) {

        if (modified_since != null && modified_since.size() > 1) {
            throw new EwpWebApplicationException("Not allow more than one value of modified_since", Response.Status.BAD_REQUEST);
        }

        //receiving_academic_year_id
        if (receiving_academic_year_id != null) {
            boolean match = true;
            Iterator<String> iterator = receiving_academic_year_id.iterator();
            while (iterator.hasNext() && match) {
                String yearId = (String) iterator.next();

                if (!yearId.matches("\\d{4}\\/\\d{4}")) {
                    match = false;
                }
            }

            if (!match) {
                throw new EwpWebApplicationException("receiving_academic_year_id is not in the correct format", Response.Status.BAD_REQUEST);
            }
        }

        IiasIndexResponse response = new IiasIndexResponse();
        List<Iia> filteredIiaList = iiasEjb.findAll();

        LOG.fine("Filtered:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

        if(!filteredIiaList.isEmpty()){
            filteredIiaList = filteredIiaList.stream().filter(iia ->
                    iia.getCooperationConditions().stream().anyMatch(c ->
                            senderHeiId.equals(c.getReceivingPartner().getInstitutionId()) ||
                                    senderHeiId.equals(c.getSendingPartner().getInstitutionId())
                    )
            ).collect(Collectors.toList());
        }

        LOG.fine("Filtered 0:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

        if (!filteredIiaList.isEmpty()) {

            filteredIiaList = new ArrayList<>(filteredIiaList);

            List<Iia> filteredIiaByReceivingAcademic = new ArrayList<>();
            if (receiving_academic_year_id != null && !receiving_academic_year_id.isEmpty()) {

                for (String year_id : receiving_academic_year_id) {
                    List<Iia> filterefList = filteredIiaList.stream().filter(iia -> anyMatchBetweenReceivingAcademicYear.test(iia, year_id)).collect(Collectors.toList());

                    filteredIiaByReceivingAcademic.addAll(filterefList);
                }

                filteredIiaList = new ArrayList<Iia>(filteredIiaByReceivingAcademic);
            }

            LOG.fine("Filtered 1:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

            if (modified_since != null && !modified_since.isEmpty()) {

                Calendar calendarModifySince = Calendar.getInstance();

                List<Iia> tempFilteredModifiedSince = new ArrayList<>();

                String modifiedValue = modified_since.get(0);
                if (LOG.getLevel() != null) {
                    LOG.log(LOG.getLevel(), "\n\n\n" + modifiedValue + "\n\n\n");
                } else {
                    LOG.log(Level.FINE, "\n\n\n" + modifiedValue + "\n\n\n");
                }
                Date date = javax.xml.bind.DatatypeConverter.parseDateTime(modifiedValue).getTime();
                calendarModifySince.setTime(date);
                List<Iia> aux = filteredIiaList.stream().filter(iia -> compareModifiedSince.test(iia, calendarModifySince)).collect(Collectors.toList());
                if (aux != null) {
                    tempFilteredModifiedSince.addAll(aux);
                }

                filteredIiaList = new ArrayList<>(tempFilteredModifiedSince);

            }

            LOG.fine("Filtered 2:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));
        }

        LOG.fine("Filtered 3:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

        if(!filteredIiaList.isEmpty()){
            filteredIiaList = filteredIiaList.stream().filter(iia -> iia.getOriginal() == null).collect(Collectors.toList());
        }

        LOG.fine("Filtered INTERNAL:" + filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList()));

        if (!filteredIiaList.isEmpty()) {
            List<String> iiaIds = filteredIiaList.stream().map(Iia::getId).collect(Collectors.toList());
            LOG.fine("IIA IDs:" + iiaIds);
            response.getIiaId().addAll(iiaIds);
        }

        return javax.ws.rs.core.Response.ok(response).build();
    }

    @GET
    @Path("get_json")
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response getGetJson(@QueryParam("iia_id") List<String> iiaIdList) {
        if (iiaIdList == null || iiaIdList.isEmpty()) {
            throw new EwpWebApplicationException("No iia_id provided", Response.Status.BAD_REQUEST);
        }

        if (iiaIdList.size() > properties.getMaxIiaIds()) {
            throw new EwpWebApplicationException("Max number of IIA ids has exceeded.", Response.Status.BAD_REQUEST);
        }

        IiasGetResponse response = new IiasGetResponse();

        List<Iia> iiaList = iiaIdList.stream()
                .map(id -> iiasEjb.findById(id))
                .filter(Objects::nonNull)
                .filter(iia -> iia.getOriginal() == null)
                .collect(Collectors.toList());

        LOG.fine("GET: iiaList.isEmpty(): " + iiaList.isEmpty());
        if (!iiaList.isEmpty()) {
            String localHeiId = iiasEjb.getHeiId();

            response.getIia().addAll(iiaConverter.convertToIias(localHeiId, iiaList));
        }


        return javax.ws.rs.core.Response.ok(response).build();

    }

    @GET
    @Path("stats_json")
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response iiaGetStatsJson() {
        LOG.fine("GET: stats_json");
        IiasStatsResponse response = new IiasStatsResponse();

        List<Iia> nonApprovedIias = iiasEjb.findAllNoneApproved();
        List<Iia> approvedIias = iiasEjb.findAllApproved();
        List<Iia> justDraftIias = iiasEjb.findAllJustDraft();

        String localHeiId = iiasEjb.getHeiId();

        int iiaPartnerUnapproved = 0;
        int iiaLocallyUnapproved = 0;

        for (Iia iia : justDraftIias) {
            List<IiaApproval> iiaApprovals = iiasEjb.findIiaApproval(iia.getId());

            if (iiaApprovals != null && !iiaApprovals.isEmpty()) {
                boolean localApproved = iiaApprovals.stream().anyMatch(iiaApproval -> iiaApproval.getHeiId().equals(localHeiId));
                boolean partnerApproved = iiaApprovals.stream().anyMatch(iiaApproval -> !iiaApproval.getHeiId().equals(localHeiId));


                if (localApproved && !partnerApproved) {
                    iiaPartnerUnapproved++;
                } else if (!localApproved && partnerApproved) {
                    iiaLocallyUnapproved++;
                }
            }
        }


        response.setIiaFetchable(BigInteger.valueOf(nonApprovedIias.size()));
        response.setIiaLocalApprovedPartnerUnapproved(BigInteger.valueOf(iiaPartnerUnapproved));
        response.setIiaLocalUnapprovedPartnerApproved(BigInteger.valueOf(iiaLocallyUnapproved));
        response.setIiaBothApproved(BigInteger.valueOf(approvedIias.size()));

        return Response.ok(response).build();
    }

    private javax.ws.rs.core.Response iiaGet(List<String> iiaIdList) {
        IiasGetResponse response = new IiasGetResponse();

        List<Iia> iiaList = iiaIdList.stream()
                .map(id -> iiasEjb.findById(id))
                .filter(Objects::nonNull)
                .filter(iia -> iia.getOriginal() == null)
                .collect(Collectors.toList());

        LOG.fine("GET: iiaList.isEmpty(): " + iiaList.isEmpty());
        if (!iiaList.isEmpty()) {
            String localHeiId = iiasEjb.getHeiId();

            response.getIia().addAll(iiaConverter.convertToIias(localHeiId, iiaList));

            try {
                if (iiaList != null && !iiaList.isEmpty()) {
                    LOG.fine("GET: iiaList.size(): " + iiaList.size());
                    iiaList.forEach(iia -> {
                        LOG.fine("GET: iia.getId(): " + iia.getId());
                    });
                }
            }catch (Exception e) {
                LOG.fine("ERROR OBTAINING IIA ID");
            }
        }


        return javax.ws.rs.core.Response.ok(response).build();
    }

    @GET
    @Path("stats")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response iiaGetStats() {
        LOG.fine("------------------------------ START /iias/stats ------------------------------");
        return iiaStats();
    }

    private javax.ws.rs.core.Response iiaStats() {

        IiasStatsResponse response = new IiasStatsResponse();

        List<Iia> nonApprovedIias = iiasEjb.findAllNoneApproved();
        List<Iia> approvedIias = iiasEjb.findAllApproved();
        List<Iia> justDraftIias = iiasEjb.findAllJustDraft();

        String localHeiId = iiasEjb.getHeiId();

        int iiaPartnerUnapproved = 0;
        int iiaLocallyUnapproved = 0;

        for (Iia iia : justDraftIias) {
            List<IiaApproval> iiaApprovals = iiasEjb.findIiaApproval(iia.getId());

            if (iiaApprovals != null && !iiaApprovals.isEmpty()) {
                boolean localApproved = iiaApprovals.stream().anyMatch(iiaApproval -> iiaApproval.getHeiId().equals(localHeiId));
                boolean partnerApproved = iiaApprovals.stream().anyMatch(iiaApproval -> !iiaApproval.getHeiId().equals(localHeiId));


                if (localApproved && !partnerApproved) {
                    iiaPartnerUnapproved++;
                } else if (!localApproved && partnerApproved) {
                    iiaLocallyUnapproved++;
                }
            }
        }


        response.setIiaFetchable(BigInteger.valueOf(nonApprovedIias.size()));
        response.setIiaLocalApprovedPartnerUnapproved(BigInteger.valueOf(iiaPartnerUnapproved));
        response.setIiaLocalUnapprovedPartnerApproved(BigInteger.valueOf(iiaLocallyUnapproved));
        response.setIiaBothApproved(BigInteger.valueOf(approvedIias.size()));

        return Response.ok(response).build();
    }


    @POST
    @Path("cnr")
    @Produces(MediaType.APPLICATION_XML)
    @EwpAuthenticate
    public javax.ws.rs.core.Response cnrPost(@FormParam("iia_id") String iiaId) {
        LOG.fine("TEST: START CNR");
        if (iiaId == null || iiaId.isEmpty()) {
            throw new EwpWebApplicationException("Missing argumanets for notification.", Response.Status.BAD_REQUEST);
        }

        Collection<String> heisCoveredByCertificate;
        if (httpRequest.getAttribute("EwpRequestRSAPublicKey") != null) {
            heisCoveredByCertificate = registryClient.getHeisCoveredByClientKey((RSAPublicKey) httpRequest.getAttribute("EwpRequestRSAPublicKey"));
        } else {
            heisCoveredByCertificate = registryClient.getHeisCoveredByCertificate((X509Certificate) httpRequest.getAttribute("EwpRequestCertificate"));
        }

        if (heisCoveredByCertificate.isEmpty()) {
            throw new EwpWebApplicationException("No HEIs covered by this certificate.", Response.Status.FORBIDDEN);
        }

        String notifierHeiId = heisCoveredByCertificate.iterator().next();
        Notification notification = new Notification();
        notification.setType(NotificationTypes.IIA);
        notification.setHeiId(notifierHeiId);
        notification.setChangedElementIds(iiaId);
        notification.setNotificationDate(new Date());
        iiasEjb.insertNotification(notification);

        //Register and execute Algoria notification
        //execNotificationToAlgoria(iiaId, notifierHeiId);
        CompletableFuture.runAsync(() -> {
            try {
                List<Iia> iiaList = iiasEjb.findByPartnerId(iiaId);
                if (iiaList != null && !iiaList.isEmpty()) {
                    iiaList = iiaList.stream().filter(iia -> iia.getOriginal() != null).collect(Collectors.toList());
                    if (!iiaList.isEmpty()) {
                        ait.modify(notifierHeiId, iiaId, iiaList.get(0));
                    }else {
                        ait.addEditIiaBeforeApproval(notifierHeiId, iiaId);
                    }
                }else {
                    ait.addEditIiaBeforeApproval(notifierHeiId, iiaId);
                }


            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return javax.ws.rs.core.Response.ok(new ObjectFactory().createIiaCnrResponse(new Empty())).build();
    }

    private void execNotificationToAlgoria(String iiaId, String notifierHeiId) {

        IiaTaskService.globalProperties = properties;
        Callable<String> callableTask = IiaTaskService.createTask(iiaId, IiaTaskEnum.UPDATED, notifierHeiId);

        //Put the task in the queue
        IiaTaskService.addTask(callableTask);
    }

    BiPredicate<Iia, String> anyMatchBetweenReceivingAcademicYear = new BiPredicate<Iia, String>() {
        @Override
        public boolean test(Iia iia, String receiving_academic_year_id) {

            List<CooperationCondition> cConditions = iia.getCooperationConditions();

            Stream<CooperationCondition> stream = cConditions.stream().filter(c -> {
                if (c.getReceivingAcademicYearId() != null) {
                    if(c.getReceivingAcademicYearId().isEmpty() || c.getReceivingAcademicYearId().size() <= 1){
                        return false;
                    }
                    String firtsYear = c.getReceivingAcademicYearId().get(0).split("/")[0];
                    String lastYear = c.getReceivingAcademicYearId().get(0).split("/")[1];

                    return (receiving_academic_year_id.compareTo(firtsYear) >= 0 && receiving_academic_year_id.compareTo(lastYear) <= 0);
                }
                return false;
            });

            return !stream.collect(Collectors.toList()).isEmpty();
        }
    };

    BiPredicate<Iia, Calendar> compareModifiedSince = new BiPredicate<Iia, Calendar>() {
        @Override
        public boolean test(Iia iia, Calendar calendarModifySince) {

            if (iia.getModifyDate() == null) {
                return true;
            }
            Calendar calendarModify = Calendar.getInstance();
            calendarModify.setTime(iia.getModifyDate());

            return calendarModify.after(calendarModifySince);
        }
    };
}
