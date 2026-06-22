package eu.erasmuswithoutpaper.iia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigInteger;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgoriaIiaStatsDto {
    private BigInteger iiaFetchable = BigInteger.ZERO;
    private BigInteger iiaLocalUnapprovedPartnerApproved = BigInteger.ZERO;
    private BigInteger iiaLocalApprovedPartnerUnapproved = BigInteger.ZERO;
    private BigInteger iiaBothApproved = BigInteger.ZERO;

    public BigInteger getIiaFetchable() {
        return iiaFetchable;
    }

    public void setIiaFetchable(BigInteger iiaFetchable) {
        this.iiaFetchable = valueOrZero(iiaFetchable);
    }

    public BigInteger getIiaLocalUnapprovedPartnerApproved() {
        return iiaLocalUnapprovedPartnerApproved;
    }

    public void setIiaLocalUnapprovedPartnerApproved(BigInteger iiaLocalUnapprovedPartnerApproved) {
        this.iiaLocalUnapprovedPartnerApproved = valueOrZero(iiaLocalUnapprovedPartnerApproved);
    }

    public BigInteger getIiaLocalApprovedPartnerUnapproved() {
        return iiaLocalApprovedPartnerUnapproved;
    }

    public void setIiaLocalApprovedPartnerUnapproved(BigInteger iiaLocalApprovedPartnerUnapproved) {
        this.iiaLocalApprovedPartnerUnapproved = valueOrZero(iiaLocalApprovedPartnerUnapproved);
    }

    public BigInteger getIiaBothApproved() {
        return iiaBothApproved;
    }

    public void setIiaBothApproved(BigInteger iiaBothApproved) {
        this.iiaBothApproved = valueOrZero(iiaBothApproved);
    }

    private BigInteger valueOrZero(BigInteger value) {
        return value != null ? value : BigInteger.ZERO;
    }
}
