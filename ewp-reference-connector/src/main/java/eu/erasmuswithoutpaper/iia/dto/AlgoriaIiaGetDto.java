package eu.erasmuswithoutpaper.iia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import eu.erasmuswithoutpaper.api.iias.endpoints.IiasGetResponse;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgoriaIiaGetDto {
    private IiasGetResponse.Iia iia;

    public IiasGetResponse.Iia getIia() {
        return iia;
    }

    public void setIia(IiasGetResponse.Iia iia) {
        this.iia = iia;
    }
}
