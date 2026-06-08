package eu.erasmuswithoutpaper.iia.control;

import eu.erasmuswithoutpaper.api.iias.endpoints.IiasGetResponse;
import eu.erasmuswithoutpaper.iia.approval.entity.IiaApproval;
import eu.erasmuswithoutpaper.iia.entity.*;
import eu.erasmuswithoutpaper.notification.entity.Notification;
import eu.erasmuswithoutpaper.omobility.las.entity.Signature;
import eu.erasmuswithoutpaper.organization.entity.Contact;
import eu.erasmuswithoutpaper.organization.entity.Institution;
import eu.erasmuswithoutpaper.organization.entity.LanguageItem;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.*;
import java.util.stream.Collectors;

@Stateless
public class IiasEJB {

    @PersistenceContext(unitName = "connector")
    EntityManager em;

    @Inject
    IiaConverter iiaConverter;

    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(IiasEJB.class.getCanonicalName());

    public List<Iia> findAll() {
        return em.createNamedQuery(Iia.findAll).getResultList();
    }

    public List<String> findIndexIds(String senderHeiId, List<String> receivingAcademicYearIds, Date modifiedSince) {
        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT DISTINCT i.id FROM Iia i ");
        jpql.append("JOIN i.cooperationConditions cc ");
        jpql.append("JOIN cc.sendingPartner sp ");
        jpql.append("JOIN cc.receivingPartner rp ");

        boolean filterByAcademicYear = receivingAcademicYearIds != null && !receivingAcademicYearIds.isEmpty();
        if (filterByAcademicYear) {
            jpql.append("JOIN cc.receivingAcademicYearId ray ");
        }

        jpql.append("WHERE i.original IS NULL ");
        jpql.append("AND (sp.institutionId = :senderHeiId OR rp.institutionId = :senderHeiId) ");

        if (filterByAcademicYear) {
            jpql.append("AND ray IN :receivingAcademicYearIds ");
        }

        if (modifiedSince != null) {
            jpql.append("AND (i.modifyDate IS NULL OR i.modifyDate > :modifiedSince) ");
        }

        TypedQuery<String> query = em.createQuery(jpql.toString(), String.class)
                .setParameter("senderHeiId", senderHeiId);

        if (filterByAcademicYear) {
            query.setParameter("receivingAcademicYearIds", receivingAcademicYearIds);
        }

        if (modifiedSince != null) {
            query.setParameter("modifiedSince", modifiedSince);
        }

        return query.getResultList();
    }

    public Iia findById(String iiaId) {
        return em.find(Iia.class, iiaId);
    }

    public List<Iia> findByIdList(String iiaId) {
        return em.createNamedQuery(Iia.findById).setParameter("id", iiaId).getResultList();
    }

    public List<Iia> findByIdListApp(String iiaId) {
        List<Iia> iias = em.createNamedQuery(Iia.findById).setParameter("id", iiaId).getResultList();
        if (iias != null && !iias.isEmpty()) {
            iias = iias.stream().filter(iia -> iia.getOriginal() == null).collect(Collectors.toList());
        }
        return iias;
    }

    public List<Iia> findByIiaCode(String iiaCode) {
        List<Iia> iias = em.createNamedQuery(Iia.findByIiaCode).setParameter("iiaCode", iiaCode).getResultList();
        if (iias != null && !iias.isEmpty()) {
            iias = iias.stream().filter(iia -> iia.getOriginal() == null).collect(Collectors.toList());
        }
        return iias;
    }

    public List<Iia> findByPartnerId(String id) {
        return em.createNamedQuery(Iia.findByPartnerId, Iia.class).setParameter("iiaId", id).getResultList();
    }

    public void deleteIia(Iia iia) {
        List<IiaApproval> iiaApprovals = findIiaApproval(iia.getId());
        if (iiaApprovals != null && !iiaApprovals.isEmpty()) {
            for (IiaApproval iiaApproval : iiaApprovals) {
                em.remove(iiaApproval);
            }
        }

        Iia approvedVersion = findApprovedVersion(iia.getId());
        if (approvedVersion != null) {
            deleteAssociatedIiaApprovals(approvedVersion.getId());
            em.remove(approvedVersion);
            em.flush();
        }

        Iia iiaInternal = em.find(Iia.class, iia.getId());
        if (iiaInternal != null) {
            em.remove(iiaInternal);
            em.flush();
        }
    }

    public void deleteAllIias() {
        // delete all iia approvals
        List<IiaApproval> iiaApprovals = findIiaApprovals();
        if (iiaApprovals != null && !iiaApprovals.isEmpty()) {
            for (IiaApproval iiaApproval : iiaApprovals) {
                em.remove(iiaApproval);
            }
        }
        List<Iia> iias = findAll();
        if (iias != null && !iias.isEmpty()) {
            for (Iia iia : iias) {
                em.remove(iia);
            }
        }
    }

    public List<Notification> findNotifications() {
        return em.createNamedQuery(Notification.findAll).getResultList();
    }

    public void insertNotification(Notification notification) {
        em.persist(notification);
    }

    public List<IiaApproval> findIiaApprovals() {
        return em.createNamedQuery(IiaApproval.findAll).getResultList();
    }

    public List<MobilityType> findMobilityTypes() {
        return em.createNamedQuery(MobilityType.findAll).getResultList();
    }

    public void insertIia(Iia iiaInternal) throws Exception {
        iiaInternal.setModifyDate(new Date());

        String localHeiId = getHeiId();
        iiaInternal.getCooperationConditions().sort((cc1, cc2) -> cc1.getSendingPartner().getInstitutionId().equals(localHeiId) ? 1 : -1);

        em.persist(iiaInternal);
        em.flush();


        String iiaIdGenerated = iiaInternal.getId();
        for (CooperationCondition condition : iiaInternal.getCooperationConditions()) {
            if (condition.getSendingPartner().getInstitutionId().equals(localHeiId)) {
                condition.getSendingPartner().setIiaId(iiaInternal.getId());
            }

            if (condition.getReceivingPartner().getInstitutionId().equals(localHeiId)) {
                condition.getReceivingPartner().setIiaId(iiaInternal.getId());
            }
        }

        LOG.fine("ADD: CALC HASH");
        try {
            iiaInternal.setConditionsHash(HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(localHeiId, Arrays.asList(em.find(Iia.class, iiaIdGenerated))).get(0)));
        } catch (Exception e) {
            LOG.fine("ADD: HASH ERROR, Can't calculate sha256 adding new iia");
            LOG.fine(e.getMessage());
            throw e;
        }
        LOG.fine("ADD: AFTER HASH");

        em.merge(iiaInternal);
    }

    public void updateHash(Iia iia) {
        String hash = "";

        try {
            hash = HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(getHeiId(), Collections.singletonList(iia)).get(0));
            iia.setConditionsHash(hash);
        } catch (Exception e) {
            LOG.fine(e.getMessage());
        }
        em.merge(iia);
        em.flush();

    }

    public String updateHash(String id) {
        Iia iia = em.find(Iia.class, id);
        String hash = "";

        LOG.fine("UPDATE HASH: CALC HASH");
        try {
            hash = HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(getHeiId(), Collections.singletonList(iia)).get(0));
            iia.setConditionsHash(hash);
        } catch (Exception e) {
            LOG.fine("UPDATE HASH: HASH ERROR, Can't calculate sha256 updating iia");
            LOG.fine(e.getMessage());
        }
        LOG.fine("UPDATE HASH: AFTER HASH");

        em.merge(iia);
        em.flush();

        return hash;
    }

    public Iia insertReceivedIia(IiasGetResponse.Iia sendIia, Iia newIia) {
        String localHeiId = getHeiId();
        for (CooperationCondition cc : newIia.getCooperationConditions()) {
            for (IiasGetResponse.Iia.Partner partner : sendIia.getPartner()) {
                if (!partner.getHeiId().equals(localHeiId)) {
                    cc.getSendingPartner().setIiaCode(partner.getIiaCode());
                    cc.getSendingPartner().setIiaId(partner.getIiaId());
                }
            }
        }

        LOG.fine("AuxIiaThread_ADDEDIT: After setting partner id");
        newIia.setHashPartner(sendIia.getIiaHash());

        newIia.setModifyDate(new Date());

        em.persist(newIia);
        em.flush();

        LOG.fine("AuxIiaThread_ADDEDIT: Iia persisted: " + newIia.getId());

        for (CooperationCondition condition : newIia.getCooperationConditions()) {
            if (condition.getSendingPartner().getInstitutionId().equals(localHeiId)) {
                condition.getSendingPartner().setIiaId(newIia.getId());
            }

            if (condition.getReceivingPartner().getInstitutionId().equals(localHeiId)) {
                condition.getReceivingPartner().setIiaId(newIia.getId());
            }
        }

        em.merge(newIia);
        em.flush();

        /*LOG.fine("AuxIiaThread_ADDEDIT: Calculate hash");
        try {
            newIia.setConditionsHash(HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(localHeiId, Arrays.asList(em.find(Iia.class, newIia.getId()))).get(0)));
            LOG.fine("AuxIiaThread_ADDEDIT: HASH CALCULATED: " + newIia.getConditionsHash());
        } catch (Exception e) {
            LOG.fine("AuxIiaThread_ADDEDIT: HASH ERROR, Can't calculate sha256 adding new iia");
            LOG.fine(e.getMessage());
        }
        LOG.fine("AuxIiaThread_ADDEDIT: After hash");

        em.merge(newIia);
        em.flush();*/

        return newIia;
    }

    public Iia saveApprovedVersion(Iia originalIia, Date modifyDate, String hashPartner) {
        List<Iia> iiaApprovals = em.createNamedQuery(Iia.findByOriginalIiaId, Iia.class).setParameter("iiaId", originalIia.getId()).getResultList();
        if (iiaApprovals != null && !iiaApprovals.isEmpty()) {
            for (Iia iiaApproval : iiaApprovals) {
                deleteAssociatedIiaApprovals(iiaApproval.getId());
                em.remove(iiaApproval);
            }
        }

        // clone IIA
        Iia approvedIia = new Iia();
        approvedIia.setModifyDate(modifyDate);

        approvedIia.setInEfect(originalIia.isInEfect());
        approvedIia.setHashPartner(hashPartner);
        approvedIia.setIiaCode(originalIia.getIiaCode());
        approvedIia.setStartDate(originalIia.getStartDate());
        approvedIia.setEndDate(originalIia.getEndDate());
        approvedIia.setSigningDate(originalIia.getSigningDate());
        approvedIia.setOriginal(originalIia);
        if (originalIia.getConditionsTerminatedAsAWhole() != null && originalIia.getConditionsTerminatedAsAWhole()) {
            approvedIia.setConditionsTerminatedAsAWhole(true);
        }

        if (originalIia.getCooperationConditions() != null) {
            approvedIia.setCooperationConditions(new ArrayList<>());

            for (CooperationCondition cc : originalIia.getCooperationConditions()) {
                approvedIia.getCooperationConditions().add(cc);
            }
        }
        approvedIia.setConditionsHash(originalIia.getConditionsHash());

        em.persist(approvedIia);
        em.flush();

        return approvedIia;

    }

    public void update(Iia iia) {
        em.merge(iia);
        em.flush();
    }

    public void updateHashPartner(String iiaId, String hashPartner) {
        Iia iia = em.find(Iia.class, iiaId);
        iia.setHashPartner(hashPartner);
        em.merge(iia);
        em.flush();
    }

    public void updateWithPartnerIDs(Iia localIia, IiasGetResponse.Iia sendIia, String iiaId, String heiId) {
        String otherIiaCode = sendIia.getPartner().stream().filter(p -> p.getHeiId().equals(heiId)).map(IiasGetResponse.Iia.Partner::getIiaCode).findFirst().orElse(null);
        for (CooperationCondition condition : localIia.getCooperationConditions()) {
            if (condition.getSendingPartner().getInstitutionId().equals(heiId)) {
                LOG.fine("AuxIiaThread_ADDEDIT: Partner " + condition.getSendingPartner().getInstitutionId() + " ID set to: " + iiaId);
                condition.getSendingPartner().setIiaId(iiaId);
                condition.getSendingPartner().setIiaCode(otherIiaCode);
            }

            if (condition.getReceivingPartner().getInstitutionId().equals(heiId)) {
                LOG.fine("AuxIiaThread_ADDEDIT: Partner " + condition.getReceivingPartner().getInstitutionId() + " ID set to: " + iiaId);
                condition.getReceivingPartner().setIiaId(iiaId);
                condition.getReceivingPartner().setIiaCode(otherIiaCode);
            }
        }
        LOG.fine("AuxIiaThread_ADDEDIT: Partners hash set to: " + sendIia.getIiaHash());
        localIia.setHashPartner(sendIia.getIiaHash());
        em.merge(localIia);
        em.flush();

        /*String localHeiId = getHeiId();

        LOG.fine("UPDATE: CALC HASH");
        try {
            localIia.setConditionsHash(HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(localHeiId, Arrays.asList(em.find(Iia.class, localIia.getId()))).get(0)));
        } catch (Exception e) {
            LOG.fine("UPDATE: HASH ERROR, Can't calculate sha256 updating iia");
            LOG.fine(e.getMessage());
        }

        em.merge(localIia);
        em.flush();*/
    }

    public String updateIia(Iia iiaInternal, Iia foundIia, String partnerHash) {
        String localHeiId = getHeiId();
        foundIia.setModifyDate(new Date());
        //em.merge(foundIia);

        foundIia.setInEfect(iiaInternal.isInEfect());
        foundIia.setHashPartner(iiaInternal.getHashPartner());
        foundIia.setIiaCode(iiaInternal.getIiaCode());
        foundIia.setStartDate(iiaInternal.getStartDate());
        foundIia.setEndDate(iiaInternal.getEndDate());
        foundIia.setSigningDate(iiaInternal.getSigningDate());

        foundIia.setConditionsTerminatedAsAWhole(null);

        foundIia.setCooperationConditions(new ArrayList<>());
        em.merge(foundIia);
        em.flush();

        if (foundIia.getCooperationConditions() != null) {
            for (CooperationCondition cc : foundIia.getCooperationConditions()) {
                CooperationCondition ccFound = em.find(CooperationCondition.class, cc.getId());
                em.remove(ccFound);
                em.flush();
            }
        }

        if (iiaInternal.getCooperationConditions() != null) {
            for (CooperationCondition cc : iiaInternal.getCooperationConditions()) {
                em.persist(cc);
                em.flush();
                foundIia.getCooperationConditions().add(cc);
            }

            foundIia.getCooperationConditions().sort((cc1, cc2) -> cc1.getSendingPartner().getInstitutionId().equals(localHeiId) ? 1 : -1);
        }

        em.merge(foundIia);
        em.flush();

        String newHash = "";

        LOG.fine("UPDATE: CALC HASH");
        try {
            newHash = HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(this.getHeiId(), Collections.singletonList(findById(foundIia.getId()))).get(0));
            foundIia.setConditionsHash(newHash);
        } catch (Exception e) {
            LOG.fine("UPDATE: HASH ERROR, Can't calculate sha256 updating iia");
            LOG.fine(e.getMessage());
        }

        if (partnerHash != null) {
            LOG.fine("UPDATE *ESPECIAL*: PARTNER HASH SET TO: " + partnerHash);
            foundIia.setHashPartner(partnerHash);
        }

        em.merge(foundIia);
        em.flush();

        return newHash;
    }

    public List<Iia> getByPartnerId(String heiId, String partnerId) {
        return em.createNamedQuery(Iia.findByPartnerAndId).setParameter("heiId", heiId).setParameter("iiaId", partnerId).getResultList();
    }

    public List<Iia> getByPartner(String heiId) {
        return em.createNamedQuery(Iia.findByPartner).setParameter("heiId", heiId).getResultList();
    }

    public void insertIiaApproval(IiaApproval iiaApproval) {
        em.persist(iiaApproval);
        em.flush();

        Iia iia = em.find(Iia.class, iiaApproval.getIia().getId());
        List<String> heiIds = new ArrayList<>();
        if (iia != null) {
            heiIds.addAll(iia.getCooperationConditions().stream().map(c -> c.getSendingPartner().getInstitutionId()).collect(java.util.stream.Collectors.toList()));
            heiIds.addAll(iia.getCooperationConditions().stream().map(c -> c.getReceivingPartner().getInstitutionId()).collect(java.util.stream.Collectors.toList()));

            if (heiIds.stream().allMatch(heiId -> {
                List<IiaApproval> list = findIiaApproval(heiId, iiaApproval.getIia().getId());
                return (list != null && !list.isEmpty());
            })) {
                iia.setInEfect(true);
                em.merge(iia);
                em.flush();

                String heiId = getHeiId();

                List<IiasGetResponse.Iia> iiaResponse = iiaConverter.convertToIias(heiId, Collections.singletonList(iia));
                Iia newIia = new Iia();
                iiaConverter.convertToIia(iiaResponse.get(0), newIia, findAllInstitutions());

                Iia clonedIia = saveApprovedVersion(newIia, iia.getModifyDate(), iia.getHashPartner());

                List<IiaApproval> list = findIiaApproval(iiaApproval.getIia().getId());
                if (list != null && !list.isEmpty()) {
                    for (IiaApproval approval : list) {
                        IiaApproval newApproval = new IiaApproval();
                        newApproval.setIiaCode(approval.getIiaCode());
                        newApproval.setStartDate(approval.getStartDate());
                        newApproval.setEndDate(approval.getEndDate());
                        newApproval.setModifyDate(approval.getModifyDate());
                        newApproval.setConditionsHash(approval.getConditionsHash());
                        newApproval.setPdf(approval.getPdf());
                        newApproval.setHeiId(approval.getHeiId());

                        newApproval.setIia(clonedIia);

                        em.persist(newApproval);
                    }
                }
            }
        }
    }

    public List<IiaApproval> findIiaApproval(String heiId, String iiaId) {
        return em.createNamedQuery(IiaApproval.findByIiaIdAndHeiId, IiaApproval.class).setParameter("heiId", heiId).setParameter("iiaId", iiaId).getResultList();
    }

    public List<IiaApproval> findIiaApproval(String iiaId) {
        return em.createNamedQuery(IiaApproval.findByIiaId, IiaApproval.class).setParameter("iiaId", iiaId).getResultList();
    }

    public void deleteAssociatedIiaApprovals(String iiaId) {
        List<IiaApproval> iiaApprovals = em.createNamedQuery(IiaApproval.findByIiaId, IiaApproval.class).setParameter("iiaId", iiaId).getResultList();
        for (IiaApproval iiaApproval : iiaApprovals) {
            em.remove(iiaApproval);
        }
    }


    public List<Institution> findAllInstitutions() {
        return em.createNamedQuery(Institution.findAll).getResultList();
    }

    public String getHeiId() {
        List<Institution> institutions = em.createNamedQuery(Institution.findAll).getResultList();
        return institutions.get(0).getInstitutionId();
    }

    public Iia findApprovedVersion(String iiaId) {
        List<Iia> iiaApprovals = em.createNamedQuery(Iia.findByOriginalIiaId, Iia.class).setParameter("iiaId", iiaId).getResultList();
        if (iiaApprovals != null && !iiaApprovals.isEmpty()) {
            return iiaApprovals.get(0);
        }
        return null;
    }

    public Iia removeIiaAndApprovedVersion(String iiaId, String approvedId) {
        deleteAssociatedIiaApprovals(approvedId);
        Iia approvedIia = em.find(Iia.class, approvedId);
        em.remove(approvedIia);
        em.flush();

        deleteAssociatedIiaApprovals(iiaId);
        deleteIia(em.find(Iia.class, iiaId));
        em.flush();

        approvedIia.setId(iiaId);
        return approvedIia;
    }

    public void revertIia(String iiaId, String approvedId) {
        List<IiaApproval> iiaApprovals = em.createNamedQuery(IiaApproval.findByIiaId, IiaApproval.class).setParameter("iiaId", approvedId).getResultList();
        Map<String, List<String>> recYer = new HashMap<>();
        em.find(Iia.class, approvedId).getCooperationConditions().forEach(cc -> {
            if (cc.getReceivingAcademicYearId() != null && !cc.getReceivingAcademicYearId().isEmpty()) {
                LOG.fine("ReceivingAcademicYearId: " + cc.getReceivingAcademicYearId().toString());
                recYer.put(cc.getId(), new ArrayList<>(cc.getReceivingAcademicYearId()));
            }
        });
        LOG.fine("Map: " + recYer.toString());
        Iia iia = removeIiaAndApprovedVersion(iiaId, approvedId);
        LOG.fine("Map2: " + recYer.toString());
        iia.getCooperationConditions().forEach(cc -> {
            cc.setReceivingAcademicYearId(recYer.getOrDefault(cc.getId(), new ArrayList<>()));
        });
        String localHeiId = getHeiId();
        iia.getCooperationConditions().sort((cc1, cc2) -> cc1.getSendingPartner().getInstitutionId().equals(localHeiId) ? 1 : -1);
        em.persist(iia);
        em.flush();
        iiaApprovals.forEach(approval -> {
            approval.setIia(iia);
            em.persist(approval);
        });
        em.flush();

        String heiId = getHeiId();

        List<IiasGetResponse.Iia> iiaResponse = iiaConverter.convertToIias(heiId, Collections.singletonList(iia));
        Iia newIia = new Iia();
        iiaConverter.convertToIia(iiaResponse.get(0), newIia, findAllInstitutions());

        Iia clonedIia = saveApprovedVersion(newIia, iia.getModifyDate(), iia.getHashPartner());

        List<IiaApproval> list = findIiaApproval(iiaId);
        if (list != null && !list.isEmpty()) {
            for (IiaApproval approval : list) {
                IiaApproval newApproval = new IiaApproval();
                newApproval.setIiaCode(approval.getIiaCode());
                newApproval.setStartDate(approval.getStartDate());
                newApproval.setEndDate(approval.getEndDate());
                newApproval.setModifyDate(approval.getModifyDate());
                newApproval.setConditionsHash(approval.getConditionsHash());
                newApproval.setPdf(approval.getPdf());
                newApproval.setHeiId(approval.getHeiId());

                newApproval.setIia(clonedIia);

                em.persist(newApproval);
            }
        }

        //iia.setConditionsHash(computeHash(iia));
        em.merge(iia);

        em.flush();
    }

    public String computeHash(Iia iia) {

        String hash = "";
        try {
            hash = HashCalculationUtility.calculateSha256(iiaConverter.convertToIias(getHeiId(), Collections.singletonList(iia)).get(0));
        } catch (Exception e) {
            return null;
        }

        return hash;
    }

    public void terminateIia(String iiaId) {
        deleteAssociatedIiaApprovals(iiaId);

        Iia iia = em.find(Iia.class, iiaId);
        iia.setConditionsTerminatedAsAWhole(true);
        iia.setInEfect(false);

        //iia.setConditionsHash(computeHash(iia));

        em.merge(iia);
        em.flush();
    }

    public List<Iia> findByDateRange(Date initDate, Date finDate) {
        return em.createNamedQuery(Iia.findByDateRange, Iia.class).setParameter("initDate", initDate).setParameter("finDate", finDate).getResultList();
    }

    public boolean isApproved(String iiaId) {
        List<IiaApproval> iiaApprovals = findIiaApproval(iiaId);
        List<IiaApproval> distinctHeiIds = new ArrayList<>();
        if (iiaApprovals == null) {
            return false;
        }

        for (IiaApproval iiaApproval : iiaApprovals) {
            if (distinctHeiIds.stream().noneMatch(d -> d.getHeiId().equals(iiaApproval.getHeiId()))) {
                distinctHeiIds.add(iiaApproval);
            }
        }

        return distinctHeiIds.size() >= 2;
    }

    public void updateHash(String localId, String newHash) {
        Iia iia = em.find(Iia.class, localId);
        iia.setConditionsHash(newHash);
        em.merge(iia);
        em.flush();
    }

    public Iia findOriginal(String iiaId) {
        return em.createNamedQuery(Iia.findById, Iia.class).setParameter("id", iiaId).getSingleResult();
    }

    public List<Iia> findApprovedVersions() {
        return em.createNamedQuery(Iia.findByOriginalIiaIdNotNull, Iia.class).getResultList();
    }

    public List<String> findIiaIds(Boolean approved, String heiId) {
        List<Iia> results = em.createNamedQuery(Iia.findByApprovalAndHei, Iia.class)
                .setParameter("approved", approved)
                .setParameter("heiId", heiId)
                .getResultList();

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        return results.stream()
                .map(Iia::getId)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Iia> findAllNoneApproved() {
        return em.createNamedQuery(Iia.findAllNonApproved, Iia.class).getResultList();
    }

    public List<Iia> findAllApproved() {
        return em.createNamedQuery(Iia.findAllApproved, Iia.class).getResultList();
    }

    public List<Iia> findAllJustDraft() {
        return em.createNamedQuery(Iia.findAllJustDraft, Iia.class).getResultList();
    }
}
