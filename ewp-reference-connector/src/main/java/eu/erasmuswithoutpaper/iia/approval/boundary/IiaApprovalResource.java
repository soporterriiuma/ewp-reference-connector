package eu.erasmuswithoutpaper.iia.approval.boundary;

import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
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

import eu.erasmuswithoutpaper.iia.common.IiaTaskEnum;
import eu.erasmuswithoutpaper.iia.control.IiasEJB;
import eu.erasmuswithoutpaper.iia.entity.CooperationCondition;
import eu.erasmuswithoutpaper.iia.entity.Iia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.iias.approval.IiasApprovalResponse;
import eu.erasmuswithoutpaper.api.iias.approval.cnr.ObjectFactory;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;
import eu.erasmuswithoutpaper.common.control.RegistryClient;
import eu.erasmuswithoutpaper.common.control.RestClient;
import eu.erasmuswithoutpaper.error.control.EwpWebApplicationException;
import eu.erasmuswithoutpaper.iia.approval.control.IiaApprovalConverter;
import eu.erasmuswithoutpaper.iia.approval.entity.IiaApproval;
import eu.erasmuswithoutpaper.iia.common.IiaTaskService;
import eu.erasmuswithoutpaper.imobility.control.IncomingMobilityConverter;
import eu.erasmuswithoutpaper.notification.entity.Notification;
import eu.erasmuswithoutpaper.notification.entity.NotificationTypes;
import eu.erasmuswithoutpaper.organization.entity.Institution;
import eu.erasmuswithoutpaper.security.EwpAuthenticate;

@Path("iiasApproval")
public class IiaApprovalResource {

    @EJB
    IiasEJB iiasEJB;

    @Inject
    GlobalProperties properties;

    @Inject
    RegistryClient registryClient;

    @Inject
    IiaApprovalConverter iiaApprovalConverter;

    @Context
    HttpServletRequest httpRequest;

    @Inject
    AuxIiaApprovalThread aipt;

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(IiaApprovalResource.class.getCanonicalName());


    @GET
    @Path("get")
    @EwpAuthenticate
    @Produces(MediaType.APPLICATION_XML)
    public javax.ws.rs.core.Response iiasApprovalGet(@QueryParam("iia_id") List<String> iiaIdList) {
        return iiaApprovalGet(iiaIdList);
    }

    @POST
    @Path("get")
    @EwpAuthenticate
    @Produces(MediaType.APPLICATION_XML)
    public javax.ws.rs.core.Response iiasApprovalPost(@FormParam("iia_id") List<String> iiaIdList) {
        return iiaApprovalGet(iiaIdList);
    }

    @GET
    @Path("get_json")
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response iiasApprovalJson(@QueryParam("iia_id") List<String> iiaIdList, @QueryParam("hei_id") String notifierHeiId) {
        if (iiaIdList.size() > properties.getMaxIiaIds()) {
            throw new EwpWebApplicationException("Max number of IIA APPROVAL id's has exceeded.", Response.Status.BAD_REQUEST);
        }

        if (iiaIdList.isEmpty()) {
            throw new EwpWebApplicationException("iia_id required.", Response.Status.BAD_REQUEST);
        }

        IiasApprovalResponse response = new IiasApprovalResponse();

        LOG.fine("iiaIdList: " + iiaIdList);

        iiaIdList.forEach(iiaId -> {
            List<Iia> iiaApproval = iiasEJB.getByPartnerId(notifierHeiId, iiaId);
            if (iiaApproval != null && !iiaApproval.isEmpty()) {
                LOG.fine("iiaApproval: " + iiaApproval.size());
                Iia iia = iiaApproval.get(0);
                IiasApprovalResponse.Approval approval = new IiasApprovalResponse.Approval();
                approval.setIiaId(iiaId);
                approval.setIiaHash(iia.getHashPartner());

                List<IiaApproval> iiaApprovals = iiasEJB.findIiaApproval(iiasEJB.getHeiId(), iia.getId());
                if (!iiaApprovals.isEmpty()) {
                    LOG.fine("iiaApprovals: " + iiaApprovals.size());
                    response.getApproval().add(approval);
                } else {
                    Iia approvedIia = iiasEJB.findApprovedVersion(iia.getId());
                    if (approvedIia != null) {
                        approval = new IiasApprovalResponse.Approval();
                        approval.setIiaId(iiaId);
                        approval.setIiaHash(approvedIia.getHashPartner());

                        response.getApproval().add(approval);
                    }
                }
            }
        });

        return javax.ws.rs.core.Response.ok(response).build();
    }

    @POST
    @Path("cnr")
    @EwpAuthenticate
    @Produces(MediaType.APPLICATION_XML)
    public javax.ws.rs.core.Response cnrPost(@FormParam("iia_id") String iiaApprovalId) {
        if (iiaApprovalId == null || iiaApprovalId.isEmpty()) {
            throw new EwpWebApplicationException("Missing arguments for notification, iia_id is required.", Response.Status.BAD_REQUEST);
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
        String localHeiId = iiasEJB.getHeiId();

        //Checking if owner_hei_id is covered by the list of institutions from the server
        List<Institution> institutionList = iiasEJB.findAllInstitutions();
        boolean ownerHeiIdCoverd = institutionList.stream().anyMatch(institution -> localHeiId.equals(institution.getInstitutionId()));
        if (!ownerHeiIdCoverd) {
            throw new EwpWebApplicationException("The owner_hei_id is not covered by the server.", Response.Status.BAD_REQUEST);
        }

        //Checking if the approvingHeiId is covered by the client certificate before create the notification
        Notification notification = new Notification();
        notification.setType(NotificationTypes.IIAAPPROVAL);
        notification.setHeiId(notifierHeiId);
        notification.setChangedElementIds(iiaApprovalId);
        notification.setNotificationDate(new Date());
        iiasEJB.insertNotification(notification);

        //Register and execute Algoria notification
        execNotificationToAlgoria(iiaApprovalId, notifierHeiId);
        CompletableFuture.runAsync(() -> {
            try {
                aipt.getApprovedIias(notifierHeiId, iiaApprovalId);
            } catch (Exception e) {
                LOG.fine("Error in AuxIiaApprovalThread: " + e.getMessage());
            }
        });


        return javax.ws.rs.core.Response.ok(new ObjectFactory().createIiaApprovalCnrResponse(new Empty())).build();
    }

    private void execNotificationToAlgoria(String iiaApprovalId, String approvingHeiId) {

        IiaTaskService.globalProperties = properties;
        Callable<String> callableTask = IiaTaskService.createTask(iiaApprovalId, IiaTaskEnum.APPROVED, approvingHeiId);

        //Put the task in the queue
        IiaTaskService.addTask(callableTask);
    }

    private javax.ws.rs.core.Response iiaApprovalGet(List<String> iiaIdList) {
        if (iiaIdList.size() > properties.getMaxIiaIds()) {
            throw new EwpWebApplicationException("Max number of IIA APPROVAL id's has exceeded.", Response.Status.BAD_REQUEST);
        }

        if (iiaIdList.isEmpty()) {
            throw new EwpWebApplicationException("iia_id required.", Response.Status.BAD_REQUEST);
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

        IiasApprovalResponse response = new IiasApprovalResponse();

        LOG.fine("iiaIdList: " + iiaIdList);

        iiaIdList.forEach(iiaId -> {
            List<Iia> iiaApproval = iiasEJB.getByPartnerId(notifierHeiId, iiaId);
            if (iiaApproval != null && !iiaApproval.isEmpty()) {
                LOG.fine("iiaApproval: " + iiaApproval.size());
                Iia iia = iiaApproval.get(0);
                IiasApprovalResponse.Approval approval = new IiasApprovalResponse.Approval();
                approval.setIiaId(iiaId);
                approval.setIiaHash(iia.getHashPartner());

                List<IiaApproval> iiaApprovals = iiasEJB.findIiaApproval(iiasEJB.getHeiId(), iia.getId());
                if (!iiaApprovals.isEmpty()) {
                    LOG.fine("iiaApprovals: " + iiaApprovals.size());
                    response.getApproval().add(approval);
                } else {
                    Iia approvedIia = iiasEJB.findApprovedVersion(iia.getId());
                    if (approvedIia != null) {
                        approval = new IiasApprovalResponse.Approval();
                        approval.setIiaId(iiaId);
                        approval.setIiaHash(approvedIia.getHashPartner());

                        response.getApproval().add(approval);
                    }
                }
            }
        });


        //Initialize the result list
        /*List<IiaApproval> iiaApprovalList = new ArrayList<IiaApproval>();

        //Getting all agreements which corresponds to the list of identifiers
        List<Iia> iiaList = iiaIdList.stream().map(id -> em.find(Iia.class, id)).filter(iia -> iia != null).collect(Collectors.toList());
        if (!iiaList.isEmpty()) {
            //Extract the iia ids
            List<String> ids = iiaList.stream().map(iia -> {
                return iia.getId();
            }).collect(Collectors.toList());

            //Get the list of iiaApproval
            if (!ids.isEmpty()) {
                iiaApprovalList = ids.stream().map(id -> em.find(IiaApproval.class, id)).filter(iia -> iia != null).collect(Collectors.toList());
            }

        }

        if (!iiaApprovalList.isEmpty()) {
            response.getApproval().addAll(iiaApprovalConverter.convertToIiasApproval(iiaApprovalList));
        }*/

        return javax.ws.rs.core.Response.ok(response).build();
    }

    BiPredicate<Iia, Map<String, String>> equalCheckHeiId = new BiPredicate<Iia, Map<String, String>>() {
        @Override
        public boolean test(Iia iia, Map<String, String> heiIds) {
            List<CooperationCondition> cConditions = iia.getCooperationConditions();

            String owner_hei_id = heiIds.get("Owner");
            String hei_id = heiIds.get("HeiId");

            Stream<CooperationCondition> stream = cConditions.stream().filter(c -> ((c.getSendingPartner().getInstitutionId().equals(owner_hei_id) && c.getReceivingPartner().getInstitutionId().equals(hei_id))
                    || (c.getReceivingPartner().getInstitutionId().equals(owner_hei_id) && c.getSendingPartner().getInstitutionId().equals(hei_id))));

            return !stream.collect(Collectors.toList()).isEmpty();
        }
    };

}
