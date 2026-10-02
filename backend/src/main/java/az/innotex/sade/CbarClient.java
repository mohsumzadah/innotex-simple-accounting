package az.innotex.sade;

import java.time.LocalDate;

/** Mərkəzi Bankın gündəlik məzənnə XML-ni gətirir (testlərdə saxta ilə əvəz olunur) */
public interface CbarClient {
    String fetchXml(LocalDate date) throws Exception;
}
