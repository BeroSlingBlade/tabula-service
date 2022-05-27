package nl.beroco.tools.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonPropertyOrder({
        "json",
        "csv",
        "tsv"
})
public class CommandLineResponse {

    @JsonProperty("json")
    private String json;
    @JsonProperty("csv")
    private List<String> csv;
    @JsonProperty("tsv")
    private List<String> tsv;
    @JsonProperty("error")
    private String error;

    @JsonProperty("json")
    public String getJson() {
        return json;
    }

    @JsonProperty("json")
    public void setJson(String json) {
        this.json = json;
    }

    public CommandLineResponse withJson(String json) {
        this.json = json;
        return this;
    }

    @JsonProperty("csv")
    public List<String> getCsv() {
        if (this.csv == null) {
            return new ArrayList<String>();
        } else {
            return csv;
        }
    }

    @JsonProperty("csv")
    public void setCsv(List<String> csv) {
        this.csv = csv;
    }

    public CommandLineResponse withCsv(List<String> csv) {
        this.csv = csv;
        return this;
    }

    @JsonProperty("tsv")
    public List<String> getTsv() {
        if (this.tsv == null) {
            return new ArrayList<String>();
        } else {
            return tsv;
        }
    }

    @JsonProperty("tsv")
    public void setTsv(List<String> tsv) {
        this.tsv = tsv;
    }

    public CommandLineResponse withTsv(List<String> tsv) {
        this.tsv = tsv;
        return this;
    }

    @JsonProperty("error")
    public String getError() {
        return error;
    }

    @JsonProperty("error")
    public void setError(String error) {
        this.error = error;
    }

    public CommandLineResponse withError(String error) {
        this.error = error;
        return this;
    }
}
