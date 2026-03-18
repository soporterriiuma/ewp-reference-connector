package eu.erasmuswithoutpaper.omobility.las.boundary;

import eu.erasmuswithoutpaper.PublicAPI;
import eu.erasmuswithoutpaper.api.architecture.ManifestApiEntryBase;
import eu.erasmuswithoutpaper.api.client.auth.methods.cliauth.httpsig.CliauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.httpsig.SrvauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.tlscert.SrvauthTlscert;
import eu.erasmuswithoutpaper.api.omobilities.las.OmobilityLas;
import eu.erasmuswithoutpaper.api.omobilities.las.cnr.OmobilityLaCnr;
import eu.erasmuswithoutpaper.api.specs.sec.intro.HttpSecurityOptions;
import eu.erasmuswithoutpaper.common.boundary.ManifestEntryStrategy;
import eu.erasmuswithoutpaper.common.control.EwpConstants;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;

import javax.inject.Inject;
import java.math.BigInteger;

@PublicAPI
public class OutgoingMobilityLearningAgreementsCnrManifestEntry implements ManifestEntryStrategy {
    @Inject
    GlobalProperties globalProperties;
    
    @Override
    public ManifestApiEntryBase getManifestEntry(String baseUri) {
    	OmobilityLaCnr mobilitieslas = new OmobilityLaCnr();
        mobilitieslas.setVersion(EwpConstants.OUTGOING_MOBILITIES_CNR_CLIENT_VERSION);
        mobilitieslas.setUrl(baseUri + "omobilities/las/cnr");
        //mobilitieslas.setStatsUrl(baseUri + "omobilities/las/cnr/stats");
        
        mobilitieslas.setMaxOmobilityIds(BigInteger.valueOf(globalProperties.getMaxOmobilitylasIds()));

        HttpSecurityOptions httpSecurityOptions = new HttpSecurityOptions();
        
        HttpSecurityOptions.ClientAuthMethods clientAuthMethods = new HttpSecurityOptions.ClientAuthMethods();

        /*CliauthTlscert cliauthtlscert = new CliauthTlscert();
        cliauthtlscert.setAllowsSelfSigned(true);
        clientAuthMethods.getAny().add(cliauthtlscert);*/

        clientAuthMethods.getAny().add(new CliauthHttpsig());
        
        httpSecurityOptions.setClientAuthMethods(clientAuthMethods);
        
        HttpSecurityOptions.ServerAuthMethods serverAuthMethods = new HttpSecurityOptions.ServerAuthMethods();
        
        serverAuthMethods.getAny().add(new SrvauthTlscert());
        serverAuthMethods.getAny().add(new SrvauthHttpsig());
        
        httpSecurityOptions.setServerAuthMethods(serverAuthMethods);
        mobilitieslas.setHttpSecurity(httpSecurityOptions);
        return mobilitieslas;
    }
}
