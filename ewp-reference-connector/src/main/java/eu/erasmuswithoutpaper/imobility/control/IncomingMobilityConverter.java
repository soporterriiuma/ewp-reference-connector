package eu.erasmuswithoutpaper.imobility.control;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.datatype.DatatypeConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.erasmuswithoutpaper.api.imobilities.tors.endpoints.ImobilityTorsGetResponse;
import eu.erasmuswithoutpaper.common.control.ConverterHelper;
import eu.erasmuswithoutpaper.imobility.entity.IMobility;
import eu.erasmuswithoutpaper.imobility.entity.IMobilityStatus;
import eu.erasmuswithoutpaper.omobility.entity.Mobility;

public class IncomingMobilityConverter {
    private static final Logger logger = LoggerFactory.getLogger(IncomingMobilityConverter.class);
    
    @PersistenceContext(unitName = "connector")
    EntityManager em;

	public ImobilityTorsGetResponse.Tor convertToTor(Mobility mobility) {
        ImobilityTorsGetResponse.Tor tor = new ImobilityTorsGetResponse.Tor();
        tor.setOmobilityId(mobility.getId());
        return tor;
    }
    
}
