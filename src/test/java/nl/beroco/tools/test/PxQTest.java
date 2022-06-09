package nl.beroco.tools.test;

import io.restassured.http.ContentType;
import nl.beroco.tools.PxQ;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

public class PxQTest {

    private Logger log = LoggerFactory.getLogger(PxQTest.class);

    @Test
    public void testPxQ() throws Exception {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(new Runnable() {
            public void run() {
                try {
                    PxQ pxq = new PxQ();
                    pxq.start("localhost", "7979");
                } catch (Exception e) {
                    log.info(e.getMessage());
                }
            }
        });
        executor.awaitTermination(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        String base64EncodedPdf = Base64.getEncoder()
                .encodeToString(Files.readAllBytes(Paths.get("src/test/resources/PxQ.pdf")));
        String base64EncodedErrorPdf = Base64.getEncoder()
                .encodeToString(Files.readAllBytes(Paths.get("src/test/resources/PxQ.txt")));
        String body = String.format("{ \"commandLine\": \"-l  --format CSV -p 1-LAST --guess \", \"base64EncodedPdf\": \"%s\"}", base64EncodedPdf);
        String errorBody = String.format("{ \"commandLine\": \"-l  --format CSV -p 1-LAST --guess \", \"base64EncodedPdf\": \"%s\"}", base64EncodedErrorPdf);

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(body)
        .when()
                .post("http://localhost:7979/upload/pdf")
        .then()
                .statusCode(200).body("csv[1]", equalTo("zien,of,alles"));

        given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(errorBody)
        .when()
                .post("http://localhost:7979/upload/pdf")
        .then()
                .statusCode(200).body("error", equalTo("Error: End-of-File, expected line"));
    }
}
