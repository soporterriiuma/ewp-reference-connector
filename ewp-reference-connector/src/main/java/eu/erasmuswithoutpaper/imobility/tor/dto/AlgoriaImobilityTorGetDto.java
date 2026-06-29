package eu.erasmuswithoutpaper.imobility.tor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgoriaImobilityTorGetDto {
    private Tor tor;

    public Tor getTor() {
        return tor;
    }

    public void setTor(Tor tor) {
        this.tor = tor;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tor {
        private String omobilityId;
        private String torAvailableSince;
        private String receivingAcademicYearId;
        private Learner learner;
        private Issuer issuer;
        private List<LearningOutcome> learningOutcomes = new ArrayList<>();
        private String attachment;

        public String getOmobilityId() {
            return omobilityId;
        }

        public void setOmobilityId(String omobilityId) {
            this.omobilityId = omobilityId;
        }

        public String getTorAvailableSince() {
            return torAvailableSince;
        }

        public void setTorAvailableSince(String torAvailableSince) {
            this.torAvailableSince = torAvailableSince;
        }

        public String getReceivingAcademicYearId() {
            return receivingAcademicYearId;
        }

        public void setReceivingAcademicYearId(String receivingAcademicYearId) {
            this.receivingAcademicYearId = receivingAcademicYearId;
        }

        public Learner getLearner() {
            return learner;
        }

        public void setLearner(Learner learner) {
            this.learner = learner;
        }

        public Issuer getIssuer() {
            return issuer;
        }

        public void setIssuer(Issuer issuer) {
            this.issuer = issuer;
        }

        public List<LearningOutcome> getLearningOutcomes() {
            return learningOutcomes;
        }

        public void setLearningOutcomes(List<LearningOutcome> learningOutcomes) {
            this.learningOutcomes = learningOutcomes;
        }

        public String getAttachment() {
            return attachment;
        }

        public void setAttachment(String attachment) {
            this.attachment = attachment;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Learner {
        private String globalId;
        private String givenNames;
        private String familyName;
        private String alternativeName;
        private String birthDate;

        public String getGlobalId() {
            return globalId;
        }

        public void setGlobalId(String globalId) {
            this.globalId = globalId;
        }

        public String getGivenNames() {
            return givenNames;
        }

        public void setGivenNames(String givenNames) {
            this.givenNames = givenNames;
        }

        public String getFamilyName() {
            return familyName;
        }

        public void setFamilyName(String familyName) {
            this.familyName = familyName;
        }

        public String getAlternativeName() {
            return alternativeName;
        }

        public void setAlternativeName(String alternativeName) {
            this.alternativeName = alternativeName;
        }

        public String getBirthDate() {
            return birthDate;
        }

        public void setBirthDate(String birthDate) {
            this.birthDate = birthDate;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Issuer {
        private String erasmusCode;
        private String heiId;
        private String pic;
        private String url;
        private String country;
        private String name;

        public String getErasmusCode() {
            return erasmusCode;
        }

        public void setErasmusCode(String erasmusCode) {
            this.erasmusCode = erasmusCode;
        }

        public String getHeiId() {
            return heiId;
        }

        public void setHeiId(String heiId) {
            this.heiId = heiId;
        }

        public String getPic() {
            return pic;
        }

        public void setPic(String pic) {
            this.pic = pic;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LearningOutcome {
        private String losId;
        private String losCode;
        private String loiId;
        private String loiCode;
        private String title;
        private String titleEn;
        private BigDecimal credits;
        private BigDecimal gradeNumeric;
        private String announcement;

        public String getLosId() {
            return losId;
        }

        public void setLosId(String losId) {
            this.losId = losId;
        }

        public String getLosCode() {
            return losCode;
        }

        public void setLosCode(String losCode) {
            this.losCode = losCode;
        }

        public String getLoiId() {
            return loiId;
        }

        public void setLoiId(String loiId) {
            this.loiId = loiId;
        }

        public String getLoiCode() {
            return loiCode;
        }

        public void setLoiCode(String loiCode) {
            this.loiCode = loiCode;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitleEn() {
            return titleEn;
        }

        public void setTitleEn(String titleEn) {
            this.titleEn = titleEn;
        }

        public BigDecimal getCredits() {
            return credits;
        }

        public void setCredits(BigDecimal credits) {
            this.credits = credits;
        }

        public BigDecimal getGradeNumeric() {
            return gradeNumeric;
        }

        public void setGradeNumeric(BigDecimal gradeNumeric) {
            this.gradeNumeric = gradeNumeric;
        }

        public String getAnnouncement() {
            return announcement;
        }

        public void setAnnouncement(String announcement) {
            this.announcement = announcement;
        }
    }
}
