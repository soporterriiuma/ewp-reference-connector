package eu.erasmuswithoutpaper.courses;

import eu.erasmuswithoutpaper.PublicAPI;
import eu.erasmuswithoutpaper.api.architecture.ManifestApiEntryBase;
import eu.erasmuswithoutpaper.api.client.auth.methods.cliauth.httpsig.CliauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.httpsig.SrvauthHttpsig;
import eu.erasmuswithoutpaper.api.client.auth.methods.srvauth.tlscert.SrvauthTlscert;
import eu.erasmuswithoutpaper.api.courses.Courses;
import eu.erasmuswithoutpaper.api.iias.Iias;
import eu.erasmuswithoutpaper.api.specs.sec.intro.HttpSecurityOptions;
import eu.erasmuswithoutpaper.common.boundary.ManifestEntryStrategy;
import eu.erasmuswithoutpaper.common.control.EwpConstants;
import eu.erasmuswithoutpaper.common.control.GlobalProperties;

import javax.inject.Inject;
import java.math.BigInteger;

@PublicAPI
public class CoursesManifestEntry implements ManifestEntryStrategy {
    @Inject
    GlobalProperties globalProperties;
    
    @Override
    public ManifestApiEntryBase getManifestEntry(String baseUri) {
        Courses courses = new Courses();
        courses.setVersion(EwpConstants.COURSES_VERSION);
        courses.setUrl(baseUri + "courses");
        courses.setMaxLosCodes(BigInteger.valueOf(globalProperties.getMaxLosCodes()));
        courses.setMaxLosIds(BigInteger.valueOf(globalProperties.getMaxLosIds()));

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
        courses.setHttpSecurity(httpSecurityOptions);
        
       // iias.setSendsNotifications(new Empty());
        return courses;
    }
}
