package eu.erasmuswithoutpaper.discovery.boundary;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import javax.ejb.Stateless;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.core.JsonProcessingException;

import eu.erasmuswithoutpaper.PublicAPI;
import eu.erasmuswithoutpaper.api.architecture.MultilineString;
import eu.erasmuswithoutpaper.api.architecture.StringWithOptionalLang;
import eu.erasmuswithoutpaper.api.discovery.Host;
import eu.erasmuswithoutpaper.api.discovery.Host.InstitutionsCovered;
import eu.erasmuswithoutpaper.api.discovery.Manifest;
import eu.erasmuswithoutpaper.api.registry.ApisImplemented;
import eu.erasmuswithoutpaper.api.registry.Hei;
import eu.erasmuswithoutpaper.api.registry.OtherHeiId;
import eu.erasmuswithoutpaper.common.boundary.ManifestEntryStrategy;
import eu.erasmuswithoutpaper.common.control.EwpKeyStore;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;
import eu.erasmuswithoutpaper.organization.entity.Institution;

@Stateless
@Path("")
public class ManifestResource {
	
	private final Logger LOGGER = Logger.getLogger(this.getClass().getCanonicalName());
	
    @PersistenceContext(unitName = "connector")
    EntityManager em;

    @Inject
    GlobalProperties globalProperties;
    
    @Inject
    EwpKeyStore keystoreController;

    @Inject
    @PublicAPI
    Instance<ManifestEntryStrategy> manifestEntries;

    @Context
    UriInfo uriInfo;

    @GET
    @Path("manifest-old")
    @Produces(MediaType.APPLICATION_XML)
    public String manifestOld() throws URISyntaxException, IOException {
        String content = new String(Files.readAllBytes(Paths.get(getClass().getClassLoader()
                .getResource("manifest.xml")
                .toURI())));
        return content;
    }
    
    @GET
    @Path("manifest")
    @Produces(MediaType.APPLICATION_XML)
    public javax.ws.rs.core.Response manifest() throws JsonProcessingException {
        Manifest manifest = new Manifest();
        Host host = new Host();
        
        ApisImplemented apisImplemented = new ApisImplemented();
        manifestEntries.forEach(me -> apisImplemented.getAny().add(me.getManifestEntry(getBaseUri())));
        
        host.setApisImplemented(apisImplemented);
        
        manifestEntries.forEach(me -> LOGGER.info(""+me));
        
        
        host.setInstitutionsCovered(getInstitutionsCovered());
        host.setAdminProvider(getAdminProvider());
        host.setClientCredentialsInUse(getClientCredentialsInUse());
        host.setServerCredentialsInUse(getServerCredentialsInUse());
        host.setAdminNotes(getAdminNotes());
        host.getAdminEmail().addAll(getAdminEmail());
        
        manifest.setHost(host);

        return javax.ws.rs.core.Response.ok(manifest).build();
    }
    
    private List<String> getAdminEmail() {
    	return Arrays.asList("ewp@uma.es"); // TODO this needs to be fixed so that we can change it in the BBD
    }
    
    private String getAdminProvider() {
    	return "UNIVERSIDAD DE MALAGA";
    }
    
    private InstitutionsCovered getInstitutionsCovered() {
        InstitutionsCovered institutionsCovered = new InstitutionsCovered();
        List<Institution> institutionList = em.createNamedQuery(Institution.findAll).getResultList();
        if (institutionList.size() != 1) {
        	throw new IllegalStateException("Internal error: more than one insitution covered");
        }
        Hei hei = createHei(institutionList.get(0));
        institutionsCovered.setHei(hei);
        
        return institutionsCovered;
    }

    private Host.ClientCredentialsInUse getClientCredentialsInUse() {
        Host.ClientCredentialsInUse clientCredentialsInUse = null;
        
        String certificate = keystoreController.getCertificate();
        if (certificate != null) {
            clientCredentialsInUse = new Host.ClientCredentialsInUse();
            clientCredentialsInUse.getCertificate().add(certificate);

            String rsaPublicKey = keystoreController.getRsaPublicKey();
            clientCredentialsInUse.getRsaPublicKey().add(rsaPublicKey);
        }
        
        return clientCredentialsInUse;
    }

    private Host.ServerCredentialsInUse getServerCredentialsInUse() {
        Host.ServerCredentialsInUse serverCredentialsInUse = null;
        
        String rsaPublicKey = keystoreController.getRsaPublicKey();
        if (rsaPublicKey != null) {
            serverCredentialsInUse = new Host.ServerCredentialsInUse();
            serverCredentialsInUse.getRsaPublicKey().add(rsaPublicKey);
        }
        
        return serverCredentialsInUse;
    }
    
    private Hei createHei(Institution institution) {
        Hei hei = new Hei();
        hei.setId(institution.getInstitutionId());

        institution.getOtherId().stream().map((otherId) -> {
            OtherHeiId oid = new OtherHeiId();
            oid.setType(otherId.getIdType());
            oid.setValue(otherId.getIdValue());
            return oid;
        }).forEachOrdered((oid) -> {
            hei.getOtherId().add(oid);
        });
        
        institution.getName().stream().map((name) -> {
            StringWithOptionalLang swolName = new StringWithOptionalLang();
            //swolName.setLang(name.getLang());
            swolName.setValue(name.getText());
            return swolName;
        }).forEachOrdered((name) -> {
            hei.getName().add(name);
        });
        return hei;
    }

    private MultilineString getAdminNotes() {
        MultilineString multilineString = new MultilineString();
        multilineString.setValue("This is a EWP reference connector instance.");
        return multilineString;
    }
    
    private String getBaseUri() {
        Optional<String> baseUriProperty = globalProperties.getBaseUri();
        return baseUriProperty.isPresent() ? baseUriProperty.get() + "/rest/" : uriInfo.getBaseUri().toString();
    }
}
