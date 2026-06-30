package eu.erasmuswithoutpaper.common.control;

import eu.erasmuswithoutpaper.registryclient.ApiSearchConditions;
import eu.erasmuswithoutpaper.registryclient.ClientImpl;
import eu.erasmuswithoutpaper.registryclient.ClientImplOptions;
import eu.erasmuswithoutpaper.registryclient.DefaultCatalogueFetcher;

import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.ejb.Singleton;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Singleton
public class RegistryClient {
    private static final Logger logger = LoggerFactory.getLogger(RegistryClient.class);
    private static final long REGISTRY_MIN_TIME_BETWEEN_QUERIES_MS = 60 * 60 * 1000L;
    private eu.erasmuswithoutpaper.registryclient.RegistryClient client;

    @Inject
    private GlobalProperties properties;

    @PostConstruct
    private void loadRegistryClient() {
        try {
            ClientImplOptions options = new ClientImplOptions();
            options.setCatalogueFetcher(new DefaultCatalogueFetcher(properties.getRegistryUrl()));
            options.setAutoRefreshing(properties.isRegistryAutoRefreshing());
            options.setMinTimeBetweenQueries(REGISTRY_MIN_TIME_BETWEEN_QUERIES_MS);
            options.setTimeBetweenRetries(properties.getRegistryTimeBetweenRetries());
            client = new ClientImpl(options);

            client.refresh();
        } catch (eu.erasmuswithoutpaper.registryclient.RegistryClient.RefreshFailureException ex) {
            logger.error("Can't refresh registry client", ex);
        }
    }

    public X509Certificate getCertificateKnownInEwpNetwork(X509Certificate[] certificates) {
        if (certificates == null) {
            return null;
        }

        checkClientAndRefresh();

        for (X509Certificate certificate : certificates) {
            if (client.isCertificateKnown(certificate)) {
                return certificate;
            }
        }
        return null;
    }

    public Collection<String> getHeisCoveredByCertificate(X509Certificate certificate) {
        checkClientAndRefresh();

        if (certificate != null && client.isCertificateKnown(certificate)) {
            Collection<String> heiIds = client.getHeisCoveredByCertificate(certificate);
            return heiIds;
        }

        return new ArrayList<>();
    }

    public List<HeiEntry> getEwpInstanceHeisWithUrlsNew() {
        List<HeiEntry> heis = getAllHeis();
        heis.forEach(hei -> {
            if (hei != null && hei.getId() != null) {
                hei.setUrls(new HashMap<>());
                hei.addUrls(getEwpInstanceHeiUrls(hei.getId()), "institutions_");
                hei.addUrls(getEwpOrganizationUnitHeiUrls(hei.getId()), "ounits_");
                hei.addUrls(getIiaHeiUrls(hei.getId()), "iias_");
            }
        });
        return heis;
    }

    public Map<String, String> getFactsheetHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.ECHO_NAMESPACE, "factsheet", EwpConstants.FACTSHEET_VERSION);
    }

    public List<HeiEntry> getEwpInstanceHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.INSTITUTION_NAMESPACE, "institutions", EwpConstants.INSTITUTION_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getEwpInstanceHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getEwpInstanceHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.INSTITUTION_NAMESPACE, "institutions", EwpConstants.INSTITUTION_CLIENT_VERSION);
    }

    public List<HeiEntry> getEwpOrganizationUnitHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.ORGANIZATION_UNIT_NAMESPACE, "organizational-units", EwpConstants.ORGANIZATION_UNIT_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getEwpOrganizationUnitHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getEwpOrganizationUnitHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.ORGANIZATION_UNIT_NAMESPACE, "organizational-units", EwpConstants.ORGANIZATION_UNIT_CLIENT_VERSION);
    }

    public List<HeiEntry> getCoursesReplicationHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.COURSE_REPLICATION_NAMESPACE, "simple-course-replication", EwpConstants.COURSE_REPLICATION_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getCoursesReplicationHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getCoursesReplicationHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.COURSE_REPLICATION_NAMESPACE, "simple-course-replication", EwpConstants.COURSE_REPLICATION_CLIENT_VERSION);
    }

    public List<HeiEntry> getCoursesHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.COURSES_NAMESPACE, "courses", EwpConstants.COURSES_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getCoursesHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getCoursesHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.COURSES_NAMESPACE, "courses", EwpConstants.COURSES_CLIENT_VERSION);
    }

    public List<HeiEntry> getIiaHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.IIAS_NAMESPACE, "iias", EwpConstants.IIAS_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getIiaHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getIiaHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.IIAS_NAMESPACE, "iias", EwpConstants.IIAS_CLIENT_VERSION);
    }

    public List<HeiEntry> getIiaCnrHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.IIAS_CNR_NAMESPACE, "iia-cnr", EwpConstants.IIA_CNR_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getIiaCnrHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getIiaCnrHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.IIAS_CNR_NAMESPACE, "iia-cnr", EwpConstants.IIA_CNR_CLIENT_VERSION);
    }

    public List<HeiEntry> getIiaApprovalHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.IIAS_APPROVAL_NAMESPACE, "iias-approval", EwpConstants.IIAS_APPROVAL_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getIiaApprovalHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getIiaApprovalHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.IIAS_APPROVAL_NAMESPACE, "iias-approval", EwpConstants.IIAS_APPROVAL_CLIENT_VERSION);
    }

    public List<HeiEntry> getIiaApprovalCnrHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.IIAS_APPROVAL_CNR_NAMESPACE, "iia-approval-cnr", EwpConstants.IIAS_APPROVAL_CNR_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getIiaApprovalCnrHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getIiaApprovalCnrHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.IIAS_APPROVAL_CNR_NAMESPACE, "iia-approval-cnr", EwpConstants.IIAS_APPROVAL_CNR_CLIENT_VERSION);
    }

    public List<HeiEntry> getOmobilitiesHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.OUTGOING_MOBILITIES_NAMESPACE, "omobilities", EwpConstants.OUTGOING_MOBILITIES_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getOmobilitiesHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getOmobilitiesHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.OUTGOING_MOBILITIES_NAMESPACE, "omobilities", EwpConstants.OUTGOING_MOBILITIES_CLIENT_VERSION);
    }

    public List<HeiEntry> getOmobilitiesCnrHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.OUTGOING_MOBILITIES_CNR_NAMESPACE, "omobility-cnr", EwpConstants.OUTGOING_MOBILITIES_CNR_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getOmobilitiesCnrHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getOmobilitiesCnrHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.OUTGOING_MOBILITIES_CNR_NAMESPACE, "omobility-cnr", EwpConstants.OUTGOING_MOBILITIES_CNR_CLIENT_VERSION);
    }

    public List<HeiEntry> getOmobilityLaCnrHeiUrlsWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.OUTGOING_MOBILITIES_LA_CNR_NAMESPACE, "omobility-la-cnr", EwpConstants.OUTGOING_MOBILITIES_CNR_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getOmobilitiesCnrHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getOmobilityLaCnrHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.OUTGOING_MOBILITIES_LA_CNR_NAMESPACE, "omobility-la-cnr", EwpConstants.OUTGOING_MOBILITIES_LA_CNR_CLIENT_VERSION);
    }

    public Map<String, String> getOmobilityLasHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.OUTGOING_MOBILITIES_LAS_NAMESPACE, "omobility-las", EwpConstants.OUTGOING_MOBILITIES_LAS_VERSION);
    }

    public List<HeiEntry> getOmobilityLasHeiUrlsWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.OUTGOING_MOBILITIES_LAS_NAMESPACE, "omobility-las", EwpConstants.OUTGOING_MOBILITIES_LAS_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getOmobilitiesCnrHeiUrls(hei.getId())));
        return heis;
    }

    public List<HeiEntry> getImobilitiesHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.INCOMING_MOBILITIES_NAMESPACE, "imobilities", EwpConstants.INCOMING_MOBILITIES_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getImobilitiesHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getImobilitiesHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.INCOMING_MOBILITIES_NAMESPACE, "imobilities", EwpConstants.INCOMING_MOBILITIES_CLIENT_VERSION);
    }

    public List<HeiEntry> getImobilitiesCnrHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.INCOMING_MOBILITIES_CNR_NAMESPACE, "imobility-cnr", EwpConstants.INCOMING_MOBILITY_CNR_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getImobilitiesCnrHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getImobilitiesCnrHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.INCOMING_MOBILITIES_CNR_NAMESPACE, "imobility-cnr", EwpConstants.INCOMING_MOBILITY_CNR_CLIENT_VERSION);
    }

    public List<HeiEntry> getImobilityTorsHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.INCOMING_MOBILITIES_TORS_NAMESPACE, "imobility-tors", EwpConstants.INCOMING_MOBILITIES_TORS_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getImobilityTorsCnrHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getImobilityTorsHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.INCOMING_MOBILITIES_TORS_NAMESPACE, "imobility-tors", EwpConstants.INCOMING_MOBILITIES_TORS_CLIENT_VERSION);
    }

    public Map<String, String> getImobilityTorsHeiUrlsV23(String heiId) {
        Map<String, String> v3 = getHeiUrls(heiId, EwpConstants.INCOMING_MOBILITIES_TORS_NAMESPACE, "imobility-tors", EwpConstants.INCOMING_MOBILITIES_TORS_CLIENT_VERSION);
        if(v3 == null || v3.isEmpty() ) {
            return getHeiUrls(heiId, EwpConstants.INCOMING_MOBILITIES_TORS_NAMESPACE, "imobility-tors", "2.0.0");
        }
        String heiUrl = v3.get("index-url");
        if (heiUrl == null || heiUrl.isEmpty()) {
            return getHeiUrls(heiId, EwpConstants.INCOMING_MOBILITIES_TORS_NAMESPACE, "imobility-tors", "2.0.0");
        }
        return v3;
    }

    public List<HeiEntry> getImobilityTorsCnrHeisWithUrls() {
        List<HeiEntry> heis = getHeis(EwpConstants.INCOMING_MOBILITIES_TORS_CNR_NAMESPACE, "imobility-tor-cnr", EwpConstants.INCOMING_MOBILITY_TORS_CNR_CLIENT_VERSION);
        heis.stream().forEach(hei -> hei.setUrls(getImobilityTorsCnrHeiUrls(hei.getId())));
        return heis;
    }

    public Map<String, String> getImobilityTorsCnrHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.INCOMING_MOBILITIES_TORS_CNR_NAMESPACE, "imobility-tor-cnr", EwpConstants.INCOMING_MOBILITY_TORS_CNR_CLIENT_VERSION);
    }

    public List<HeiEntry> getEchoHeis() {
        return getHeis(EwpConstants.ECHO_NAMESPACE, "echo", EwpConstants.ECHO_CLIENT_VERSION);
    }

    public Map<String, String> getEchoHeiUrls(String heiId) {
        return getHeiUrls(heiId, EwpConstants.ECHO_NAMESPACE, "echo", EwpConstants.ECHO_CLIENT_VERSION);
    }

    private Map<String, String> getHeiUrls(String heiId, String namespace, String name, String version) {
        checkClientAndRefresh();

        ApiSearchConditions myConditions = new ApiSearchConditions();
        myConditions.setApiClassRequired(namespace, name, version);
        myConditions.setRequiredHei(heiId);
        Collection<Element> manifest = client.findApis(myConditions);

        if (manifest != null) {
            return getUrlsFromManifestElement(manifest);
        } else return null;
    }

    private List<HeiEntry> getAllHeis() {
        checkClientAndRefresh();

        ApiSearchConditions myConditions = new ApiSearchConditions();

        Collection<eu.erasmuswithoutpaper.registryclient.HeiEntry> list = client.findHeis(myConditions);
        List<HeiEntry> heis = list.stream().map(e -> {
            Collection<String> erasmusIds = e.getOtherIds("erasmus");
            String erasmusId = erasmusIds.isEmpty() ? null : erasmusIds.iterator().next();
            return new HeiEntry(e.getId(), e.getName(), erasmusId);
        }).collect(Collectors.toList());

        return heis;
    }

    private List<HeiEntry> getHeis(String namespace, String name, String version) {
        checkClientAndRefresh();

        ApiSearchConditions myConditions = new ApiSearchConditions();
        myConditions.setApiClassRequired(namespace, name, version);

        Collection<eu.erasmuswithoutpaper.registryclient.HeiEntry> list = client.findHeis(myConditions);
        List<HeiEntry> heis = list.stream().map(e -> {
            Collection<String> erasmusIds = e.getOtherIds("erasmus");
            String erasmusId = erasmusIds.isEmpty() ? null : erasmusIds.iterator().next();
            return new HeiEntry(e.getId(), e.getName(), erasmusId);
        }).collect(Collectors.toList());

        return heis;
    }

    private Map<String, String> getUrlsFromManifestElement(Collection<Element> manifestElements) {
        Map<String, String> urlMap = new HashMap<>();
        for (Element manifestElement : manifestElements) {
            NodeList childNodeList = manifestElement.getChildNodes();
            for (int i = 0; i < childNodeList.getLength(); i++) {
                Node childNode = childNodeList.item(i);
                if ("url".equalsIgnoreCase(childNode.getLocalName())) {
                    urlMap.put("url", childNode.getFirstChild().getNodeValue());
                } else if ("index-url".equalsIgnoreCase(childNode.getLocalName())) {
                    urlMap.put("index-url", childNode.getFirstChild().getNodeValue());
                } else if ("get-url".equalsIgnoreCase(childNode.getLocalName())) {
                    urlMap.put("get-url", childNode.getFirstChild().getNodeValue());
                } else if ("update-url".equalsIgnoreCase(childNode.getLocalName())) {
                    urlMap.put("update-url", childNode.getFirstChild().getNodeValue());
                }
            }
        }

        return urlMap;
    }

    public RSAPublicKey findRsaPublicKey(String fingerprint) {
        checkClientAndRefresh();

        return client.findRsaPublicKey(fingerprint);
    }

    public RSAPublicKey findClientRsaPublicKey(String fingerprint) {
        checkClientAndRefresh();

        RSAPublicKey rsaPublicKey = client.findRsaPublicKey(fingerprint);
        return rsaPublicKey != null && client.isClientKeyKnown(rsaPublicKey) ? rsaPublicKey : null;
    }

    public Collection<String> getHeisCoveredByClientKey(RSAPublicKey rsapk) {
        checkClientAndRefresh();

        return client.getHeisCoveredByClientKey(rsapk);
    }

    private void checkClientAndRefresh() {
        ClientImplOptions options = new ClientImplOptions();

        Date acceptableUntil = new Date(client.getExpiryDate().getTime() + options.getMaxAcceptableStaleness());
        if ((new Date()).after(acceptableUntil)) {
            try {
                client.refresh();
            } catch (eu.erasmuswithoutpaper.registryclient.RegistryClient.RefreshFailureException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
