package eu.erasmuswithoutpaper.imobility.tor.boundary;

import eu.erasmuswithoutpaper.api.architecture.ManifestApiEntryBase;
import eu.erasmuswithoutpaper.api.client.auth.methods.cliauth.httpsig.CliauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.httpsig.SrvauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.tlscert.SrvauthTlscert;
import eu.erasmuswithoutpaper.api.imobilities.tors.stats.ImobilityTorStats;
import eu.erasmuswithoutpaper.api.specs.sec.intro.HttpSecurityOptions;
import eu.erasmuswithoutpaper.common.boundary.ManifestEntryStrategy;
import eu.erasmuswithoutpaper.common.control.EwpConstants;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;

import javax.inject.Inject;

//@PublicAPI
public class IncomingMobilityTorsStatsManifestEntry implements ManifestEntryStrategy {
    @Inject
    GlobalProperties globalProperties;
    
    @Override
    public ManifestApiEntryBase getManifestEntry(String baseUri) {
        ImobilityTorStats imobilityTorCnr = new ImobilityTorStats();
        /*imobilityTorCnr.setVersion(EwpConstants.INCOMING_MOBILITY_TORS_STATS_VERSION);
        imobilityTorCnr.setUrl(baseUri + "imobilities/tors/stats");

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
