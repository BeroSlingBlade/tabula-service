package nl.beroco.tools;

import nl.beroco.tools.json.CommandLineRequest;
import nl.beroco.tools.json.CommandLineResponse;
import nl.beroco.tools.processor.CommandLineAppProcessor;
import nl.beroco.tools.processor.ErrorProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.processor.DefaultExchangeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PxQ {

    private static final String PORT = "port";
    private static final String HOST = "host";
    private static String VERSION = "1.0.5";
    private static String VERSION_STRING = String.format("tabula %s (c) 2012-2020 Manuel Aristarán", VERSION);
    private static String BANNER = "Tabula helps you extract tables from PDFs\n\n";
    private final Logger log = LoggerFactory.getLogger(PxQ.class);
    private Main main;

    public void start(String host, String port) throws Exception {
        log.info(VERSION_STRING);
        log.info(BANNER);
        main = new Main();
        main.addRouteBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                onException(Exception.class)
                        .handled(true)
                        .process(new ErrorProcessor());

                restConfiguration()
                        .component("undertow")
                        .host(host)
                        .port(port)
                        .bindingMode(RestBindingMode.json)
                        .dataFormatProperty("prettyPrint", "true");

                rest()
                        .post("/upload/pdf").type(CommandLineRequest.class).outType(CommandLineResponse.class)
                        .to("direct:pdf");

                from("direct:pdf")
                        .onCompletion()
                            .log("Response [${body}]")
                        .end()
                        .removeHeaders("*", "breadcrumbId")
                        .process(new CommandLineAppProcessor());
            }
        });
        main.run();
    }

    public static void main(String[] args) throws Exception {
        PxQ pxq = new PxQ();
        String port = "8084";
        if (System.getProperty(PORT) != null) {
            port = System.getProperty(PORT);
        }
        String host = "localhost";
        if (System.getProperty(HOST) != null) {
            host = System.getProperty(HOST);
        }
        pxq.start(host, port);
    }
}
