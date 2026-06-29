package eu.erasmuswithoutpaper.iia.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgoriaIiaIndexDto {
    private List<String> iiaId = new ArrayList<>();

    public List<String> getIiaId() {
        return iiaId;
    }

    public void setIiaId(List<String> iiaId) {
        this.iiaId = iiaId != null ? iiaId : new ArrayList<>();
    }

    public void setIia(List<String> iia) {
        setIiaId(iia);
    }
}
