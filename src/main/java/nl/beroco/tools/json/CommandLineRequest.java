package nl.beroco.tools.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Vraag template met pdf
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "commandLine",
        "base64EncodedPdf"
})

public class CommandLineRequest {

    @JsonProperty("commandLine")
    private String commandLine;
    @JsonProperty("base64EncodedPdf")
    private String base64EncodedPdf;

    @JsonProperty("commandLine")
    public String getCommandLine() {
        return commandLine;
    }

    @JsonProperty("commandLine")
    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public CommandLineRequest withCommandLine(String commandLine) {
        this.commandLine = commandLine;
        return this;
    }

    @JsonProperty("base64EncodedPdf")
    public String getBase64EncodedPdf() {
        return base64EncodedPdf;
    }

    @JsonProperty("base64EncodedPdf")
    public void setBase64EncodedPdf(String base64EncodedPdf) {
        this.base64EncodedPdf = base64EncodedPdf;
    }

    public CommandLineRequest withBase64EncodedPdf(String base64EncodedPdf) {
        this.base64EncodedPdf = base64EncodedPdf;
        return this;
    }
}
