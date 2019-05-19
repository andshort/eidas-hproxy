package eu.eidas.sp;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileCountriesRepository implements CountriesRepositoryI{
	private List<Country> countries;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FileCountriesRepository.class);
	
	public FileCountriesRepository(Properties configs) {
		countries = new ArrayList<Country>();
		int numCountries = Integer.parseInt(configs.getProperty(Constants.COUNTRY_NUMBER));
		for (int i = 1; i <= numCountries; i++) {
			Country country = new Country(i, configs.getProperty("country" + Integer.toString(i) + ".name"), configs.getProperty("country" + Integer.toString(i) + ".url"),
					configs.getProperty("country" + Integer.toString(i) + ".countrySelector"), configs.getProperty("country" + Integer.toString(i) + ".metadata.url"));
			countries.add(country);
			LOGGER.info(country.toString());
		}
	}
	
	public String getCountryMetadataUrl(String citizencountry) {
		for (Country testcountry : countries) if (citizencountry.equals(testcountry.getName())) return testcountry.getMetadataUrl();
		return "";
	}
}