package eu.erasmuswithoutpaper.omobility.control;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.datatype.DatatypeConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.erasmuswithoutpaper.api.architecture.Empty;
import eu.erasmuswithoutpaper.api.omobilities.endpoints.MobilityActivityType;
import eu.erasmuswithoutpaper.api.omobilities.endpoints.MobilityStatus;
import eu.erasmuswithoutpaper.api.omobilities.endpoints.StudentMobility;
//import eu.erasmuswithoutpaper.api.omobilities.endpoints.StudentMobility.AdditionalRequirement;
//import eu.erasmuswithoutpaper.api.omobilities.endpoints.StudentMobility.NomineeLanguageSkill;
import eu.erasmuswithoutpaper.common.control.ConverterHelper;
import eu.erasmuswithoutpaper.omobility.entity.AdditionalRequirements;
import eu.erasmuswithoutpaper.omobility.entity.LanguageSkill;
import eu.erasmuswithoutpaper.omobility.entity.Mobility;
import eu.erasmuswithoutpaper.omobility.entity.MobilityActivityAttributes;
import eu.erasmuswithoutpaper.organization.entity.LanguageItem;
import eu.erasmuswithoutpaper.organization.entity.MobilityParticipant;
import eu.erasmuswithoutpaper.organization.entity.Person;

public class OutgoingMobilityConverter {
    /*private static final Logger logger = LoggerFactory.getLogger(OutgoingMobilityConverter.class);
    
    @PersistenceContext(unitName = "connector")
    EntityManager em;

    public StudentMobility convertToStudentMobilityForStudies(Mobility mobility) {
    	StudentMobility studentMobilityForStudies = new StudentMobility();
        
        studentMobilityForStudies.setEqfLevelStudiedAtDeparture(mobility.getEqfLevelDeparture());
        studentMobilityForStudies.setEqfLevelStudiedAtNomination(mobility.getEqfLevelNomination());
        studentMobilityForStudies.setOmobilityId(mobility.getId());
        
        studentMobilityForStudies.setSendingAcademicTermEwpId(mobility.getSendingAcademicTermEwpId());
        studentMobilityForStudies.setNonStandardMobilityPeriod(new Empty());
        studentMobilityForStudies.setReceivingAcademicYearId(mobility.getReceivingAcademicYearId());
        studentMobilityForStudies.setNomineeIscedFCode(mobility.getNomineeIscedFCode());
        
        studentMobilityForStudies.getNomineeLanguageSkill().addAll(convertToNomineeLanguageSkill(mobility.getNomineeLanguageSkill()));
        studentMobilityForStudies.getAdditionalRequirement().addAll(convertToAdditionalRequirements(mobility.getAdditionalRequirements()));
        
        try {
            studentMobilityForStudies.setPlannedArrivalDate(ConverterHelper.convertToXmlGregorianCalendar(mobility.getPlannedArrivalDate()));
        } catch (DatatypeConfigurationException ex) {
            logger.error("Can't convert date", ex);
        }
        try {
            studentMobilityForStudies.setPlannedDepartureDate(ConverterHelper.convertToXmlGregorianCalendar(mobility.getPlannedDepartureDate()));
        } catch (DatatypeConfigurationException ex) {
            logger.error("Can't convert date", ex);
        }
        studentMobilityForStudies.setReceivingHei(convertToReceivingHei(null, mobility.getReceivingInstitutionId(), mobility.getReceivingOrganizationUnitId()));
        studentMobilityForStudies.setSendingHei(convertToSendingHei(mobility.getIiaId(), mobility.getSendingInstitutionId(), mobility.getSendingOrganizationUnitId()));
        studentMobilityForStudies.setStatus(convertToMobilityStatus(mobility.getStatus()));
        studentMobilityForStudies.setStudent(convertToStudent(mobility.getMobilityParticipantId()));
        
        MobilityActivityType activityType = convertToMobilityActivityType(mobility.getActivityType());
        studentMobilityForStudies.setActivityType(activityType);
        
        eu.erasmuswithoutpaper.api.omobilities.endpoints.MobilityActivityAttributes activityAttribute = convertToMobilityAttribute(mobility.getActivityAttribute());
        studentMobilityForStudies.setActivityAttributes(activityAttribute);
        
        return studentMobilityForStudies;
    }

    private Collection<? extends AdditionalRequirement> convertToAdditionalRequirements(
			List<AdditionalRequirements> additionalRequirements) {
    	return additionalRequirements.stream().map((addreq) ->{
    		
    		AdditionalRequirement addRequirements = new AdditionalRequirement();
    		
    		addRequirements.setName(addreq.getName());
    		addRequirements.setFile(addreq.getFile());
    		addRequirements.setUrl(addreq.getUrl());
    		
    		return addRequirements;
    	}).collect(Collectors.toList());
	}

	private eu.erasmuswithoutpaper.api.omobilities.endpoints.MobilityActivityAttributes convertToMobilityAttribute(MobilityActivityAttributes activityAttribute) {
    	eu.erasmuswithoutpaper.api.omobilities.endpoints.MobilityActivityAttributes mobilityActAtt = eu.erasmuswithoutpaper.api.omobilities.endpoints.MobilityActivityAttributes.fromValue(activityAttribute.value());
        return mobilityActAtt;
	}

	private Collection<NomineeLanguageSkill> convertToNomineeLanguageSkill(
			List<LanguageSkill> nomineeLanguageSkill) {
		
    	return nomineeLanguageSkill.stream().map((langskill) ->{
    		
    		NomineeLanguageSkill nomineeLangSkill = new NomineeLanguageSkill();
    		
    		nomineeLangSkill.setCefrLevel(langskill.getCefrLevel());
    		nomineeLangSkill.setLanguage(langskill.getLanguage());
    		return nomineeLangSkill;
    	}).collect(Collectors.toList());
	}

    private StudentMobility.ReceivingHei convertToReceivingHei(String iiaId, String institutionId, String organizationUnitId) {
        StudentMobility.ReceivingHei receivingHei = new StudentMobility.ReceivingHei();
        receivingHei.setIiaId(iiaId);
        receivingHei.setHeiId(institutionId);
        receivingHei.setOunitId(organizationUnitId);
        return receivingHei;
    }

    private StudentMobility.SendingHei convertToSendingHei(String iiaId, String institutionId, String organizationUnitId) {
        StudentMobility.SendingHei sendingHei = new StudentMobility.SendingHei();
        sendingHei.setIiaId(iiaId);
        sendingHei.setHeiId(institutionId);
        sendingHei.setOunitId(organizationUnitId);
        return sendingHei;
    }

    private MobilityStatus convertToMobilityStatus(eu.erasmuswithoutpaper.omobility.entity.MobilityStatus status) {
        MobilityStatus mobilityStatus = MobilityStatus.fromValue(status.value());
        return mobilityStatus;
    }
    
    private MobilityActivityType convertToMobilityActivityType(eu.erasmuswithoutpaper.omobility.entity.MobilityActivityType activityType) {
    	MobilityActivityType mobilityStatus = MobilityActivityType.fromValue(activityType.value());
        return mobilityStatus;
    }

    private StudentMobility.Student convertToStudent(String studentId) {
        MobilityParticipant student =  em.find(MobilityParticipant.class, studentId);
        StudentMobility.Student mobilityStudent = new StudentMobility.Student();
        if (student != null) {
            Person person = student.getPerson();
            mobilityStudent.getPhoneNumber().addAll(ConverterHelper.convertToPhoneNumbers(student.getContactDetails().getPhoneNumber()));
            
            try {
                mobilityStudent.setBirthDate(ConverterHelper.convertToXmlGregorianCalendar(person.getBirthDate()));
            } catch (DatatypeConfigurationException ex) {
                logger.error("Can't convert date", ex);
            }
            
            mobilityStudent.setGlobalId(person.getGlobalId());

            mobilityStudent.setCitizenship(person.getCountryCode());
            if (student.getContactDetails().getEmail() != null && student.getContactDetails().getEmail().size() > 0) {
                mobilityStudent.setEmail(student.getContactDetails().getEmail().get(0));
            }

            
            mobilityStudent.getFamilyName().addAll(ConverterHelper.convertToStringWithOptionalLang(Arrays.asList(new LanguageItem(person.getLastName(), LanguageItem.SWEDISH))));
            mobilityStudent.setGender(person.getGender().value());
            mobilityStudent.getGivenNames().addAll(ConverterHelper.convertToStringWithOptionalLang(Arrays.asList(new LanguageItem(person.getFirstNames(), LanguageItem.SWEDISH))));
            mobilityStudent.setMailingAddress(ConverterHelper.convertToFlexibleAddress(student.getContactDetails().getMailingAddress()));
            mobilityStudent.setStreetAddress(ConverterHelper.convertToFlexibleAddress(student.getContactDetails().getStreetAddress()));
        }
        return mobilityStudent;
    }*/
}
