package eu.erasmuswithoutpaper;

import eu.erasmuswithoutpaper.courses.CoursesResource;
import eu.erasmuswithoutpaper.courses.CoursesResourceReplication;
import eu.erasmuswithoutpaper.discovery.boundary.ManifestResource;
import eu.erasmuswithoutpaper.echo.boundary.EchoResource;
import eu.erasmuswithoutpaper.factsheet.boundary.FactsheetResource;
import eu.erasmuswithoutpaper.iia.approval.boundary.IiaApprovalResource;
import eu.erasmuswithoutpaper.iia.boundary.IiaResource;
import eu.erasmuswithoutpaper.imobility.boundary.IncomingMobilityResource;
import eu.erasmuswithoutpaper.monitoring.TestMonitoringEndpoint;
import eu.erasmuswithoutpaper.omobility.boundary.OutgoingMobilityResource;
import eu.erasmuswithoutpaper.omobility.las.boundary.OutgoingMobilityLearningAgreementsResource;
import eu.erasmuswithoutpaper.organization.boundary.InstitutionResource;
import eu.erasmuswithoutpaper.organization.boundary.OrganizationUnitResource;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("rest")
public class RestJAXRSConfiguration extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> resources = new HashSet<>();
        resources.add(EchoResource.class);
        resources.add(ManifestResource.class);
        resources.add(InstitutionResource.class);
        //resources.add(CoursesResource.class);
        //resources.add(CoursesResourceReplication.class);
        
        resources.add(OrganizationUnitResource.class);
        //resources.add(LosResource.class);
        resources.add(OutgoingMobilityResource.class);
        resources.add(IncomingMobilityResource.class);

        resources.add(IiaResource.class);
        resources.add(IiaApprovalResource.class);
        resources.add(FactsheetResource.class);
        resources.add(OutgoingMobilityLearningAgreementsResource.class);
        
        resources.add(TestMonitoringEndpoint.class);
        return resources;
    }
}
