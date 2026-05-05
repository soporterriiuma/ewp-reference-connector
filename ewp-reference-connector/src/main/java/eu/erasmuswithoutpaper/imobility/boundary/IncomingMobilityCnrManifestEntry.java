package eu.erasmuswithoutpaper.imobility.boundary;

import java.math.BigInteger;

import javax.inject.Inject;

import eu.erasmuswithoutpaper.api.architecture.ManifestApiEntryBase;
import eu.erasmuswithoutpaper.api.client.auth.methods.cliauth.httpsig.CliauthHttpsig;
//import eu.erasmuswithoutpaper.api.client.auth.methods.cliauth.tlscert.CliauthTlscert;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.httpsig.SrvauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.tlscert.SrvauthTlscert;
import eu.erasmuswithoutpaper.api.imobilities.cnr.ImobilityCnr;
import eu.erasmuswithoutpaper.api.specs.sec.intro.HttpSecurityOptions;
import eu.erasmuswithoutpaper.common.boundary.ManifestEntryStrategy;
import eu.erasmuswithoutpaper.common.control.EwpConstants;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;

public class IncomingMobilityCnrManifestEntry implements ManifestEntryStrategy {
    @Inject
    GlobalProperties globalProperties;
    
    @Override
    public ManifestApiEntryBase getManifestEntry(String baseUri) {
        ImobilityCnr imobilityCnr = new ImobilityCnr();
        /*imobilityCnr.setVersion(EwpConstants.INCOMING_MOBILITY_CNR_VERSION);
        imobilityCnr.setMaxOmobilityIds(BigInteger.valueOf(globalProperties.getMaxMobilityIds()));
        imobilityCnr.setUrl(baseUri + "imobilities/cnr");
        
        HttpSecurityOptions httpSecurityOptions = new HttpSecurityOptions();
        
        HttpSecurityOptions.ClientAuthMethods clientAuthMethods = new HttpSecurityOptions.ClientAuthMethods();

        clientAuthMethods.getAny().add(new CliauthHttpsig());
        
        httpSecurityOptions.setClientAuthMethods(clientAuthMethods);
        
        HttpSecurityOptions.ServerAuthMethods serverAuthMethods = new HttpSecurityOptions.ServerAuthMethods();
        
        serverAuthMethods.getAny().add(new SrvauthTlscert());
        serverAuthMethods.getAny().add(new SrvauthHttpsig());
        
        httpSecurityOptions.setServerAuthMethods(serverAuthMethods);
        imobilityCnr.setHttpSecurity(httpSecurityOptions);*/

        return imobilityCnr;
    }
}
