package eu.erasmuswithoutpaper.courses;

import eu.erasmuswithoutpaper.PublicAPI;
import eu.erasmuswithoutpaper.api.architecture.ManifestApiEntryBase;
import eu.erasmuswithoutpaper.api.client.auth.methods.cliauth.httpsig.CliauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.httpsig.SrvauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.tlscert.SrvauthTlscert;
import eu.erasmuswithoutpaper.api.courses.Courses;
import eu.erasmuswithoutpaper.api.courses.replication.SimpleCourseReplication;
import eu.erasmuswithoutpaper.api.specs.sec.intro.HttpSecurityOptions;
import eu.erasmuswithoutpaper.common.boundary.ManifestEntryStrategy;
import eu.erasmuswithoutpaper.common.control.EwpConstants;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;

import javax.inject.Inject;
import java.math.BigInteger;

@PublicAPI
public class CoursesReplicationManifestEntry implements ManifestEntryStrategy {
    @Inject
    GlobalProperties globalProperties;
    
    @Override
    public ManifestApiEntryBase getManifestEntry(String baseUri) {
        SimpleCourseReplication replication = new SimpleCourseReplication();
        replication.setVersion(EwpConstants.COURSE_REPLICATION_VERSION);
        replication.setUrl(baseUri + "courses/replication");
        replication.setSupportsModifiedSince(true);

        HttpSecurityOptions httpSecurityOptions = new HttpSecurityOptions();
        
        HttpSecurityOptions.ClientAuthMethods clientAuthMethods = new HttpSecurityOptions.ClientAuthMethods();
        
        /*CliauthTlscert cliauthtlscert = new CliauthTlscert();
        cliauthtlscert.setAllowsSelfSigned(false);
        clientAuthMethods.getAny().add(cliauthtlscert);*/
        
//        clientAuthMethods.getAny().add(new Anonymous());
        
        clientAuthMethods.getAny().add(new CliauthHttpsig());
        
        httpSecurityOptions.setClientAuthMethods(clientAuthMethods);
        
        HttpSecurityOptions.ServerAuthMethods serverAuthMethods = new HttpSecurityOptions.ServerAuthMethods();
        
        serverAuthMethods.getAny().add(new SrvauthTlscert());
        serverAuthMethods.getAny().add(new SrvauthHttpsig());
        
        httpSecurityOptions.setServerAuthMethods(serverAuthMethods);
        replication.setHttpSecurity(httpSecurityOptions);
        
       // iias.setSendsNotifications(new Empty());
        return replication;
    }
}
