package az.innotex.sade;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class HttpCbarClient implements CbarClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public String fetchXml(LocalDate date) throws Exception {
        String url = "https://www.cbar.az/currencies/" + date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + ".xml";
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("CBAR HTTP " + res.statusCode());
        return res.body();
    }
}
