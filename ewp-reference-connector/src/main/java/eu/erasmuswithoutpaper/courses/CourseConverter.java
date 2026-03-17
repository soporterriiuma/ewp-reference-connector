package eu.erasmuswithoutpaper.courses;

import eu.erasmuswithoutpaper.api.architecture.StringWithOptionalLang;
import eu.erasmuswithoutpaper.api.types.academicterm.AcademicTerm;
import eu.erasmuswithoutpaper.courses.dto.AlgoriaLOIApiResponse;
import eu.erasmuswithoutpaper.courses.dto.AlgoriaLOPKApiResponse;
import https.github_com.erasmus_without_paper.ewp_specs_api_courses.tree.stable_v1.CoursesResponse;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.Collectors;

public class CourseConverter {

    public static CoursesResponse.LearningOpportunitySpecification convert(AlgoriaLOPKApiResponse algoriaLOApiResponse) {
        if (algoriaLOApiResponse == null) {
            return null;
        }
        CoursesResponse.LearningOpportunitySpecification los = new CoursesResponse.LearningOpportunitySpecification();
        los.setLosId(algoriaLOApiResponse.getElement().getLos_id());
        los.setLosCode(algoriaLOApiResponse.getElement().getLos_code());
        los.setOunitId(algoriaLOApiResponse.getElement().getOrganizational_unit_id());
        los.getTitle().addAll(algoriaLOApiResponse.getElement().getTitle().stream().map(title -> {
            StringWithOptionalLang t = new StringWithOptionalLang();
            t.setLang(title.getLang());
            t.setValue(title.getValue());
            return t;
        }).collect(Collectors.toList()));
        los.setType(paseType(algoriaLOApiResponse.getElement().getType()));

        los.setContains(convert(algoriaLOApiResponse.getElement().getChildren_los()));
        return los;
    }

    private static CoursesResponse.LearningOpportunitySpecification.Contains convert(List<String> childrenLos) {
        if (childrenLos == null || childrenLos.isEmpty()) {
            return null;
        }
        CoursesResponse.LearningOpportunitySpecification.Contains contains = new CoursesResponse.LearningOpportunitySpecification.Contains();
        contains.getLosId().addAll(childrenLos);
        return contains;
    }

    public static CoursesResponse.LearningOpportunitySpecification.Specifies convert(AlgoriaLOIApiResponse learningOutcome) {
        if (learningOutcome == null) {
            return null;
        }


        CoursesResponse.LearningOpportunitySpecification.Specifies specifies = new CoursesResponse.LearningOpportunitySpecification.Specifies();
        specifies.getLearningOpportunityInstance().addAll(learningOutcome.getElements().stream().map(loi -> {
            CoursesResponse.LearningOpportunitySpecification.Specifies.LearningOpportunityInstance loiInstance = new CoursesResponse.LearningOpportunitySpecification.Specifies.LearningOpportunityInstance();
            loiInstance.setLoiId(convertToLOI(loi.getLoi_id()));
            if(loi.getAcademic_term() != null) {
                loiInstance.setStart(toXMLGregorianCalendar(loi.getAcademic_term().getStart_date()));
                loiInstance.setEnd(toXMLGregorianCalendar(loi.getAcademic_term().getEnd_date()));
                loiInstance.setAcademicTerm(convert(loi.getAcademic_term()));
            }
            if(loi.getEcts_credits() != null) {
                CoursesResponse.LearningOpportunitySpecification.Specifies.LearningOpportunityInstance.Credit credit = new CoursesResponse.LearningOpportunitySpecification.Specifies.LearningOpportunityInstance.Credit();
                credit.setScheme("ects");
                credit.setValue(loi.getEcts_credits());

                loiInstance.getCredit().add(credit);
            }
            if(loi.getLanguage_of_instruction() != null) {
                //serch english language if exists if not take first one
                String languageValue = loi.getLanguage_of_instruction().stream()
                        .filter(lang -> "en".equalsIgnoreCase(lang.getLang()))
                        .findFirst()
                        .orElse(loi.getLanguage_of_instruction().get(0))
                        .getValue();
                loiInstance.setLanguageOfInstruction(convertToInternational(languageValue));
            }



            return loiInstance;
        }).collect(Collectors.toList()));

        return specifies;
    }

    private static AcademicTerm convert(AlgoriaLOIApiResponse.AcademicTermLOI academicTerm) {
        if (academicTerm == null) {
            return null;
        }
        AcademicTerm at = new AcademicTerm();
        at.setAcademicYearId(convertAcademicYearId(academicTerm.getAcademic_year_id()));
        at.getDisplayName().addAll(academicTerm.getDisplay_name().stream().map(name -> {
            StringWithOptionalLang t = new StringWithOptionalLang();
            t.setLang(name.getLang());
            t.setValue(name.getValue());
            return t;
        }).collect(Collectors.toList()));
        at.setStartDate(toXMLGregorianCalendar(academicTerm.getStart_date()));
        at.setEndDate(toXMLGregorianCalendar(academicTerm.getEnd_date()));

        return at;
    }

    public static XMLGregorianCalendar toXMLGregorianCalendar(String date) {
        if (date == null) {
            return null;
        }

        try {
            // Parse date string to LocalDate
            LocalDate localDate = LocalDate.parse(date);

            // Convert LocalDate → XMLGregorianCalendar
            return DatatypeFactory.newInstance()
                    .newXMLGregorianCalendarDate(
                            localDate.getYear(),
                            localDate.getMonthValue(),
                            localDate.getDayOfMonth(),
                            DatatypeConstants.FIELD_UNDEFINED // timezone not specified
                    );

        } catch (Exception e) {
            throw new RuntimeException("Error converting String to XMLGregorianCalendar", e);
        }
    }

    private static String paseType(String type) {
        if (type == null) {
            return null;
        }

        String[] words = type.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(
                        Character.toUpperCase(word.charAt(0))
                );
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }

        return result.toString().trim();
    }

    private static String convertToLOI(String loiId) {
        if (loiId == null) {
            return null;
        }
        if (loiId.startsWith("CR/")) {
            return loiId.replaceFirst("CR/", "CRI/");
        }
        if (loiId.startsWith("CLS/")) {
            return loiId.replaceFirst("CLS/", "CLSI/");
        }
        if (loiId.startsWith("MOD/")) {
            return loiId.replaceFirst("MOD/", "MODI/");
        }
        if (loiId.startsWith("DEP/")) {
            return loiId.replaceFirst("DEP/", "DEPI/");
        }
        return loiId;
    }

    private static String convertAcademicYearId(String academicYearId) {
        if (academicYearId == null) {
            return null;
        }
        // Convert "2023/24" to "2023/2024"
        String[] parts = academicYearId.split("/");
        if (parts.length == 2) {
            String startYear = parts[0];
            String endYear = parts[1];
            if (endYear.length() == 2) {
                endYear = startYear.substring(0, 2) + endYear;
            }
            return startYear + "/" + endYear;
        }
        return academicYearId;
    }

    private static String convertToInternational(String language) {
        if (language == null) {
            return null;
        }
        switch (language.toLowerCase()) {
            case "english":
            case "inglés":
                return "en-US";
            case "french":
            case "francés":
                return "fr-FR";
            case "german":
            case "alemán":
                return "de-DE";
            case "spanish":
            case "español":
                return "es-ES";
            // Add more languages as needed
            default:
                return language; // Return as is if not recognized
        }
    }

}
