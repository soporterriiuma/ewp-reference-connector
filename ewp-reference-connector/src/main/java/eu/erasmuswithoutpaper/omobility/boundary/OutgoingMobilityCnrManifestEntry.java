package eu.erasmuswithoutpaper.omobility.boundary;

import java.math.BigInteger;

import javax.inject.Inject;

import eu.erasmuswithoutpaper.PublicAPI;
import eu.erasmuswithoutpaper.api.architecture.ManifestApiEntryBase;
import eu.erasmuswithoutpaper.api.client.auth.methods.cliauth.httpsig.CliauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.httpsig.SrvauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.tlscert.SrvauthTlscert;
import eu.erasmuswithoutpaper.api.omobilities.cnr.OmobilityCnr;
import eu.erasmuswithoutpaper.api.specs.sec.intro.HttpSecurityOptions;
import eu.erasmuswithoutpaper.common.boundary.ManifestEntryStrategy;
import eu.erasmuswithoutpaper.common.control.EwpConstants;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;

public class OutgoingMobilityCnrManifestEntry implements ManifestEntryStrategy {
    @Inject
    GlobalProperties globalProperties;
    
    @Override
    public ManifestApiEntryBase getManifestEntry(String baseUri) {
        OmobilityCnr omobilityCnr = new OmobilityCnr();
        /*omobilityCnr.setVersion(EwpConstants.OUTGOING_MOBILITY_CNR_VERSION);
        omobilityCnr.setMaxOmobilityIds(BigInteger.valueOf(globalProperties.getMaxMobilityIds()));
        omobilityCnr.setUrl(baseUri + "omobilities/cnr");

        HttpSecurityOptions httpSecurityOptions = new HttpSecurityOptions();

        HttpSecurityOptions.ClientAuthMethods clientAuthMethods = new HttpSecurityOptions.ClientAuthMethods();

        clientAuthMethods.getAny().add(new CliauthHttpsig());

        httpSecurityOptions.setClientAuthMethods(clientAuthMethods);

        HttpSecurityOptions.ServerAuthMethods serverAuthMethods = new HttpSecurityOptions.ServerAuthMethods();

        serverAuthMethods.getAny().add(new SrvauthTlscert());
        serverAuthMethods.getAny().add(new SrvauthHttpsig());

        httpSecurityOptions.setServerAuthMethods(serverAuthMethods);
        omobilityCnr.setHttpSecurity(httpSecurityOptions);*/

        return omobilityCnr;
    }
}
