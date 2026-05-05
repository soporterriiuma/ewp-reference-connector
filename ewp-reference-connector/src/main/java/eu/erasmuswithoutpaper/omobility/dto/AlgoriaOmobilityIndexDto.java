package eu.erasmuswithoutpaper.omobility.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AlgoriaOmobilityIndexDto {
    private List<String> elements = new ArrayList<>();
    private Integer total_elements;
    private String info;

    public List<String> getElements() {
        return elements;
    }

    public void setElements(List<String> elements) {
        this.elements = elements;
    }

    public Integer getTotal_elements() {
        return total_elements;
    }

    public void setTotal_elements(Integer total_elements) {
        this.total_elements = total_elements;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }
}
