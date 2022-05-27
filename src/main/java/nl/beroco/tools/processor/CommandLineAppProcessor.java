package nl.beroco.tools.processor;

import nl.beroco.tools.json.CommandLineRequest;
import nl.beroco.tools.json.CommandLineResponse;
import nl.beroco.tools.tabula.JacksonJsonWriter;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.cli.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import technology.tabula.*;
import technology.tabula.detectors.DetectionAlgorithm;
import technology.tabula.detectors.NurminenDetectionAlgorithm;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import technology.tabula.writers.CSVWriter;
import technology.tabula.writers.JSONWriter;
import technology.tabula.writers.TSVWriter;
import technology.tabula.writers.Writer;

import java.io.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;


public class CommandLineAppProcessor implements Processor {

    private static final int RELATIVE_AREA_CALCULATION_MODE = 0;
    private static final int ABSOLUTE_AREA_CALCULATION_MODE = 1;
    private final Logger log = LoggerFactory.getLogger(CommandLineAppProcessor.class);
    private Appendable defaultOutput;

    @Override
    public void process(Exchange exchange) throws Exception {
        CommandLineRequest request = exchange.getIn().getBody(CommandLineRequest.class);
        log.info(String.format("Processing commandline [%s]", request.getCommandLine()));
        log.info(String.format("Base64 encoded pdf [%-20.20s...]", request.getBase64EncodedPdf()));
        String[] args = request.getCommandLine().split(" ");
        CommandLineParser parser = new DefaultParser();
        try {
            // parse the command line arguments
            CommandLine line = parser.parse(buildOptions(), args);
            OutputFormat outputFormat = whichOutputFormat(line);

            StringWriter writer = (StringWriter) extractFile(request, line, outputFormat);
            BufferedReader b = new BufferedReader(new StringReader(writer.toString()));
            ArrayList<String> lines =  new ArrayList<>();
            b.lines().forEach(l -> {lines.add(l);});
            CommandLineResponse response = new CommandLineResponse();
            switch (outputFormat) {
                case CSV:
                    exchange.getIn().setBody(response.withCsv(lines), CommandLineResponse.class);
                    break;
                case JSON:
                    exchange.getIn().setBody(response.withJson(writer.toString()), String.class);
                    break;
                case TSV:
                    exchange.getIn().setBody(response.withTsv(lines), CommandLineResponse.class);
                    break;
                case SIMPLE:
                    exchange.getIn().setBody(String.format("{\"json\": %s}", writer.toString()), String.class);
                    break;
            }
        } catch (ParseException exp) {
            log.error("Error: " + exp.getMessage());
            exchange.getIn().setBody(new CommandLineResponse().withError(exp.getMessage()));
        }
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    }

    private static OutputFormat whichOutputFormat(CommandLine line) throws ParseException {
        if (!line.hasOption('f')) {
            return OutputFormat.CSV;
        }

        try {
            return OutputFormat.valueOf(line.getOptionValue('f'));
        } catch (IllegalArgumentException e) {
            throw new ParseException(String.format(
                    "format %s is illegal. Available formats: %s",
                    line.getOptionValue('f'),
                    Utils.join(",", OutputFormat.formatNames())));
        }
    }

    private static List<Pair<Integer, Rectangle>> whichAreas(CommandLine line) throws ParseException {
        if (!line.hasOption('a')) {
            return null;
        }

        String[] optionValues = line.getOptionValues('a');

        List<Pair<Integer, Rectangle>> areaList = new ArrayList<Pair<Integer, Rectangle>>();
        for (String optionValue : optionValues) {
            int areaCalculationMode = ABSOLUTE_AREA_CALCULATION_MODE;
            int startIndex = 0;
            if (optionValue.startsWith("%")) {
                startIndex = 1;
                areaCalculationMode = RELATIVE_AREA_CALCULATION_MODE;
            }
            List<Float> f = parseFloatList(optionValue.substring(startIndex));
            if (f.size() != 4) {
                throw new ParseException("area parameters must be top,left,bottom,right optionally preceded by %");
            }
            areaList.add(new Pair<Integer, Rectangle>(areaCalculationMode, new Rectangle(f.get(0), f.get(1), f.get(3) - f.get(1), f.get(2) - f.get(0))));
        }
        return areaList;
    }

    private static List<Integer> whichPages(CommandLine line, int lastPage) throws ParseException {
        String pagesOption = line.hasOption('p') ? line.getOptionValue('p') : "1";
        //return Utils.parsePagesOption(pagesOption);
        return parsePagesOption(pagesOption, lastPage);
    }

    // CommandLine parsing methods

    private static ExtractionMethod whichExtractionMethod(CommandLine line) {
        // -r/--spreadsheet [deprecated; use -l] or -l/--lattice
        if (line.hasOption('r') || line.hasOption('l')) {
            return ExtractionMethod.SPREADSHEET;
        }

        // -n/--no-spreadsheet [deprecated; use -t] or  -c/--columns or -g/--guess or -t/--stream
        if (line.hasOption('n') || line.hasOption('c') || line.hasOption('t')) {
            return ExtractionMethod.BASIC;
        }
        return ExtractionMethod.DECIDE;
    }

    private static TableExtractor createExtractor(CommandLine line) throws ParseException {
        TableExtractor extractor = new TableExtractor();
        extractor.setGuess(line.hasOption('g'));
        extractor.setMethod(whichExtractionMethod(line));
        extractor.setUseLineReturns(line.hasOption('u'));

        if (line.hasOption('c')) {
            String optionString = line.getOptionValue('c');
            if (optionString.startsWith("%")) {
                extractor.setVerticalRulingPositionsRelative(true);
                optionString = optionString.substring(1);
            }
            extractor.setVerticalRulingPositions(parseFloatList(optionString));
        }
        return extractor;
    }

    public static List<Float> parseFloatList(String option) throws ParseException {
        String[] f = option.split(",");
        List<Float> rv = new ArrayList<>();
        try {
            for (final String element : f) {
                rv.add(Float.parseFloat(element));
            }
            return rv;
        } catch (NumberFormatException e) {
            throw new ParseException("Wrong number syntax");
        }
    }

    public static Options buildOptions() {
        Options o = new Options();

        o.addOption("g", "guess", false, "Guess the portion of the page to analyze per page.");
        o.addOption("l", "lattice", false, "Force PDF to be extracted using lattice-mode extraction (if there are ruling lines separating each cell, as in a PDF of an Excel spreadsheet)");
        o.addOption("t", "stream", false, "Force PDF to be extracted using stream-mode extraction (if there are no ruling lines separating each cell)");
        o.addOption("u", "use-line-returns", false, "Use embedded line returns in cells. (Only in spreadsheet mode.)");
        // o.addOption("d", "debug", false, "Print detected table areas instead of processing.");
        o.addOption(Option.builder("f")
                .longOpt("format")
                .desc("Output format: (" + Utils.join(",", OutputFormat.formatNames()) + "). Default: CSV")
                .hasArg()
                .argName("FORMAT")
                .build());
        o.addOption(Option.builder("s")
                .longOpt("password")
                .desc("Password to decrypt document. Default is empty")
                .hasArg()
                .argName("PASSWORD")
                .build());
        o.addOption(Option.builder("c")
                .longOpt("columns")
                .desc("X coordinates of column boundaries. Example --columns 10.1,20.2,30.3. "
                        + "If all values are between 0-100 (inclusive) and preceded by '%', input will be taken as % of actual width of the page. "
                        + "Example: --columns %25,50,80.6")
                .hasArg()
                .argName("COLUMNS")
                .build());
        o.addOption(Option.builder("a")
                .longOpt("area")
                .desc("-a/--area = Portion of the page to analyze. Example: --area 269.875,12.75,790.5,561. "
                        + "Accepts top,left,bottom,right i.e. y1,x1,y2,x2 where all values are in points relative to the top left corner. "
                        + "If all values are between 0-100 (inclusive) and preceded by '%', input will be taken as % of actual height or width of the page. "
                        + "Example: --area %0,0,100,50. To specify multiple areas, -a option should be repeated. Default is entire page")
                .hasArg()
                .argName("AREA")
                .build());
        o.addOption(Option.builder("p")
                .longOpt("pages")
                .desc("Comma separated list of ranges, or all. Examples: --pages 1-3,5-7, --pages 4-last, --pages 3 or --pages all. Default is --pages 1")
                .hasArg()
                .argName("PAGES")
                .build());

        return o;
    }

    // utilities, etc.
    private static List<Integer> parsePagesOption(String pagesSpec, int lastpage) throws ParseException {
        if (pagesSpec.equals("all")) {
            return null;
        }
        List<Integer> rv = new ArrayList<>();

        String[] ranges = pagesSpec.split(",");
        for (int i = 0; i < ranges.length; i++) {
            String[] r = ranges[i].split("-");
            // last
            if (r.length > 0) {
                for (int a = 0; a < r.length; a++) {
                    r[a] = r[a].toLowerCase().replace("last", String.valueOf(lastpage));
                }
            }
            // end last
            if (r.length == 0 || !Utils.isNumeric(r[0]) || r.length > 1 && !Utils.isNumeric(r[1])) {
                throw new ParseException("Syntax error in page range specification");
            }

            if (r.length < 2) {
                rv.add(Integer.parseInt(r[0]));
            } else {
                int t = Integer.parseInt(r[0]);
                int f = Integer.parseInt(r[1]);
                if (t > f) {
                    throw new ParseException("Syntax error in page range specification");
                }
                rv.addAll(Utils.range(t, f + 1));
            }
        }

        Collections.sort(rv);
        return rv;
    }


    private Appendable extractFile(CommandLineRequest request, CommandLine line, OutputFormat outputFormat) throws ParseException {
        PDDocument pdfDocument = null;

        List<Pair<Integer, Rectangle>> pageAreas = whichAreas(line);
        TableExtractor tableExtractor = createExtractor(line);

        try {
            InputStream is = new ByteArrayInputStream(
                    Base64.getDecoder().decode(request.getBase64EncodedPdf()));
            if (line.hasOption('s')) {
                pdfDocument = PDDocument.load(is, line.getOptionValue('s'));
            } else {
                pdfDocument = PDDocument.load(is);
            }
            List<Integer> pages = whichPages(line, pdfDocument.getNumberOfPages());
            PageIterator pageIterator = getPageIterator(pdfDocument, pages);
            List<Table> tables = new ArrayList<>();

            while (pageIterator.hasNext()) {
                Page page = pageIterator.next();

                if (tableExtractor.verticalRulingPositions != null) {
                    for (Float verticalRulingPosition : tableExtractor.verticalRulingPositions) {
                        page.addRuling(new Ruling(0, verticalRulingPosition, 0.0f, (float) page.getHeight()));
                    }
                }

                if (pageAreas != null) {
                    for (Pair<Integer, Rectangle> areaPair : pageAreas) {
                        Rectangle area = areaPair.getRight();
                        if (areaPair.getLeft() == RELATIVE_AREA_CALCULATION_MODE) {
                            area = new Rectangle((float) (area.getTop() / 100 * page.getHeight()),
                                    (float) (area.getLeft() / 100 * page.getWidth()), (float) (area.getWidth() / 100 * page.getWidth()),
                                    (float) (area.getHeight() / 100 * page.getHeight()));
                        }
                        tables.addAll(tableExtractor.extractTables(page.getArea(area)));
                    }
                } else {
                    tables.addAll(tableExtractor.extractTables(page));
                }
            }
            StringWriter sw = new StringWriter();
            writeTables(tables, outputFormat, sw);
            return sw;
        } catch (IOException e) {
            throw new ParseException(e.getMessage());
        } finally {
            try {
                if (pdfDocument != null) {
                    pdfDocument.close();
                }
            } catch (IOException e) {
                log.error("Error in closing pdf document" + e);
            }
        }
    }

    private PageIterator getPageIterator(PDDocument pdfDocument, List<Integer> pages) throws IOException {
        ObjectExtractor extractor = new ObjectExtractor(pdfDocument);
        return (pages == null) ?
                extractor.extract() :
                extractor.extract(pages);
    }

    private void writeTables(List<Table> tables, OutputFormat outputFormat, Appendable out) throws IOException {
        Writer writer = null;
        switch (outputFormat) {
            case CSV:
                writer = new CSVWriter();
                break;
            case JSON:
                writer = new JSONWriter();
                break;
            case TSV:
                writer = new TSVWriter();
                break;
            case SIMPLE:
                writer = new JacksonJsonWriter();
                break;
        }
        writer.write(out, tables);
    }

    private enum OutputFormat {
        CSV,
        TSV,
        JSON,
        SIMPLE;

        static String[] formatNames() {
            OutputFormat[] values = OutputFormat.values();
            String[] rv = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                rv[i] = values[i].name();
            }
            return rv;
        }
    }

    private enum ExtractionMethod {
        BASIC,
        SPREADSHEET,
        DECIDE
    }

    private static class TableExtractor {
        private boolean guess = false;
        private boolean useLineReturns = false;
        private BasicExtractionAlgorithm basicExtractor = new BasicExtractionAlgorithm();
        private SpreadsheetExtractionAlgorithm spreadsheetExtractor = new SpreadsheetExtractionAlgorithm();

        private boolean verticalRulingPositionsRelative = false;
        private List<Float> verticalRulingPositions = null;

        private ExtractionMethod method = ExtractionMethod.BASIC;

        public TableExtractor() {
        }

        public void setVerticalRulingPositions(List<Float> positions) {
            this.verticalRulingPositions = positions;
        }

        public void setVerticalRulingPositionsRelative(boolean relative) {
            this.verticalRulingPositionsRelative = relative;
        }

        public void setGuess(boolean guess) {
            this.guess = guess;
        }

        public void setUseLineReturns(boolean useLineReturns) {
            this.useLineReturns = useLineReturns;
        }

        public void setMethod(ExtractionMethod method) {
            this.method = method;
        }

        public List<Table> extractTables(Page page) {
            ExtractionMethod effectiveMethod = this.method;
            if (effectiveMethod == ExtractionMethod.DECIDE) {
                effectiveMethod = spreadsheetExtractor.isTabular(page) ?
                        ExtractionMethod.SPREADSHEET :
                        ExtractionMethod.BASIC;
            }
            switch (effectiveMethod) {
                case BASIC:
                    return extractTablesBasic(page);
                case SPREADSHEET:
                    return extractTablesSpreadsheet(page);
                default:
                    return new ArrayList<>();
            }
        }

        public List<Table> extractTablesBasic(Page page) {
            if (guess) {
                // guess the page areas to extract using a detection algorithm
                // currently we only have a detector that uses spreadsheets to find table areas
                DetectionAlgorithm detector = new NurminenDetectionAlgorithm();
                List<Rectangle> guesses = detector.detect(page);
                List<Table> tables = new ArrayList<>();

                for (Rectangle guessRect : guesses) {
                    Page guess = page.getArea(guessRect);
                    tables.addAll(basicExtractor.extract(guess));
                }
                return tables;
            }

            if (verticalRulingPositions != null) {
                List<Float> absoluteRulingPositions;

                if (this.verticalRulingPositionsRelative) {
                    // convert relative to absolute
                    absoluteRulingPositions = new ArrayList<>(verticalRulingPositions.size());
                    for (float relative : this.verticalRulingPositions) {
                        float absolute = (float) (relative / 100.0 * page.getWidth());
                        absoluteRulingPositions.add(absolute);
                    }
                } else {
                    absoluteRulingPositions = this.verticalRulingPositions;
                }
                return basicExtractor.extract(page, absoluteRulingPositions);
            }

            return basicExtractor.extract(page);
        }

        public List<Table> extractTablesSpreadsheet(Page page) {
            // TODO add useLineReturns
            return spreadsheetExtractor.extract(page);
        }
    }

    private class DebugOutput {
        private boolean debugEnabled;

        public DebugOutput(boolean debug) {
            this.debugEnabled = debug;
        }

        public void debug(String msg) {
            if (this.debugEnabled) {
                System.err.println(msg);
            }
        }
    }
}

