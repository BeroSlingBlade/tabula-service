package nl.beroco.tools.processor;

import nl.beroco.tools.json.CommandLineResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class ErrorProcessor implements Processor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Exception exception = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 500);
        exchange.getIn().setBody(new CommandLineResponse().withError(exception.getMessage()));
    }
}
