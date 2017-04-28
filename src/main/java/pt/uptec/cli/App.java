package pt.uptec.cli;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static java.time.DayOfWeek.*;

/**
 * Hello world!
 */
public class App {

    private static final String BASE_URL = "http://assicanti.pt/wp-content/uploads/";

    private static final Map<DayOfWeek, String> DAY_OF_WEEK_NAMES_MAP;

    private static final List<String> FOODS = ImmutableList.of("SOPA", "CARNE", "PEIXE", "VEGETARIANO");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-YYYY");

    private static final Pattern NEWLINE = Pattern.compile("[\\r\\n]+");

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    static {
        DAY_OF_WEEK_NAMES_MAP = Maps.immutableEnumMap(ImmutableMap.<DayOfWeek, String>builder().put(MONDAY, "SEGUNDA")
                                                                                               .put(TUESDAY, "TERÇA")
                                                                                               .put(WEDNESDAY, "QUARTA")
                                                                                               .put(THURSDAY, "QUINTA")
                                                                                               .put(FRIDAY, "SEXTA")
                                                                                               .build());

    }

    public static void main(final String[] args) {
        final ZonedDateTime zdt = ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault());
        if (!DAY_OF_WEEK_NAMES_MAP.containsKey(zdt.getDayOfWeek())) {
            logger.error("Cannot get menu for {}", zdt.getDayOfWeek());
            System.exit(-1);
        }
        URL url = null;
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            url = new URL(new URL(BASE_URL), getSpec(zdt));
            logger.debug("Making get request to [{}]", url);
            final HttpGet httpGet = new HttpGet(url.toURI());
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                logger.trace("Got response: {}", response);
                final HttpEntity entity = response.getEntity();
                logger.trace("Received Entity {}", entity);
                final String contents = parse(entity.getContent());

                printFiltered(contents, zdt.getDayOfWeek());
            } catch (SAXException | IOException | TikaException e) {
                logger.error("Error parsing pdf contents.", e);
                System.exit(-1);
            }
        } catch (final IOException e) {
            logger.error("Error during request.", e);
            System.exit(-1);
        } catch (URISyntaxException e) {
            logger.error("Error in uri syntax [{}]", url, e);
            System.exit(-1);
        }
    }

    private static void printFiltered(final String contents, final DayOfWeek dayOfWeek) {
        final String nameOfDay = DAY_OF_WEEK_NAMES_MAP.get(dayOfWeek);
        final ImmutableList<String> lines = ImmutableList.copyOf(NEWLINE.split(contents));
        final int start = lines.indexOf(nameOfDay);
        if (start < 0) {
            logger.error("Not found for '{}' in pdf. (lines: {})", nameOfDay, lines);
            return;
        }
        logger.info("Menu for {}", dayOfWeek);
        lines.stream().skip(start).filter(line -> {
            final int idx = line.indexOf(' ');
            return (idx >= 0) && FOODS.contains(line.substring(0, idx));
        }).forEachOrdered(msg -> {
            logger.info("{}: {}", (Object[]) msg.split(" ", 2));
        });
    }

    private static String parse(final InputStream input) throws TikaException, SAXException, IOException {
        final Parser parser = new PDFParser();
        final ContentHandler handler = new BodyContentHandler();
        final Metadata metadata = new Metadata();
        final ParseContext parseContext = new ParseContext();

        parser.parse(input, handler, metadata, parseContext);

        return handler.toString();
    }

    private static String getSpec(final ZonedDateTime zdt) {
        final ZonedDateTime monday = zdt.with(ChronoField.DAY_OF_WEEK, MONDAY.getValue());
        final ZonedDateTime friday = zdt.with(ChronoField.DAY_OF_WEEK, FRIDAY.getValue());
        final YearMonth yearMonth = YearMonth.from(monday);
        final String spec = format("%04d/%02d/Ementa-uptec-%s-a-%s.pdf", yearMonth.getYear(), yearMonth.getMonthValue(), monday.format(FORMATTER), friday.format(FORMATTER));
        logger.trace("Spec is {}", spec);
        return spec;
    }

    private App() {}


}
