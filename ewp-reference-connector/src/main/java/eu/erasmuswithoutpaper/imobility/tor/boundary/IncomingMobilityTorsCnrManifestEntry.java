package eu.erasmuswithoutpaper.imobility.tor.boundary;

import eu.erasmuswithoutpaper.PublicAPI;
import eu.erasmuswithoutpaper.api.architecture.ManifestApiEntryBase;
import eu.erasmuswithoutpaper.api.client.auth.methods.cliauth.httpsig.CliauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.httpsig.SrvauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.tlscert.SrvauthTlscert;
import eu.erasmuswithoutpaper.api.imobilities.tors.cnr.ImobilityTorCnr;
import eu.erasmuswithoutpaper.api.specs.sec.intro.HttpSecurityOptions;
import eu.erasmuswithoutpaper.common.control.EwpConstants;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;
import javax.inject.Inject;
import eu.erasmuswithoutpaper.common.boundary.ManifestEntryStrategy;

import java.math.BigInteger;

//@PublicAPI
public class IncomingMobilityTorsCnrManifestEntry implements ManifestEntryStrategy {
    @Inject
    GlobalProperties globalProperties;
    
    @Override
    public ManifestApiEntryBase getManifestEntry(String baseUri) {
        ImobilityTorCnr imobilityTorCnr = new ImobilityTorCnr();
        /*imobilityTorCnr.setVersion(EwpConstants.INCOMING_MOBILITY_TORS_CNR_VERSION);
        imobilityTorCnr.setUrl(baseUri + "imobilities/tors/cnr");

        imobilityTorCnr.setMaxOmobilityIds(BigInteger.valueOf(globalProperties.getMaxMobilityIds()));

        HttpSecurityOptions httpSecurityOptions = new HttpSecurityOptions();

        HttpSecurityOptions.ClientAuthMethods clientAuthMethods = new HttpSecurityOptions.ClientAuthMethods();
        */
        /*CliauthTlscert cliauthtlscert = new CliauthTlscert();
        cliauthtlscert.setAllowsSelfSigned(true);
        clientAuthMethods.getAny().add(cliauthtlscert);*/
        /*
        clientAuthMethods.getAny().add(new CliauthHttpsig());

        httpSecurityOptions.setClientAuthMethods(clientAuthMethods);

        HttpSecurityOptions.ServerAuthMethods serverAuthMethods = new HttpSecurityOptions.ServerAuthMethods();

        serverAuthMethods.getAny().add(new SrvauthTlscert());
        serverAuthMethods.getAny().add(new SrvauthHttpsig());

        httpSecurityOptions.setServerAuthMethods(serverAuthMethods);
        imobilityTorCnr.setHttpSecurity(httpSecurityOptions);
        */
        return imobilityTorCnr;
    }
}
