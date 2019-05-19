package eu.eidas.sp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.eidas.auth.engine.SamlEngineSystemClock;
import eu.eidas.auth.engine.metadata.MetadataClockI;
import eu.eidas.auth.engine.metadata.impl.CachingMetadataFetcher;
import eu.eidas.auth.engine.metadata.impl.FileMetadataLoader;

@Configuration
public class AppConfig {
	private static final String FILEREPO_DIR_WRITE=System.getenv().get("eidas_config") + "EntityDescriptors/";
	
	@Bean
	public eu.eidas.auth.engine.metadata.MetadataFetcherI Fetcher(){
		CachingMetadataFetcher	fetcher = new CachingMetadataFetcher();
		File sampleNodeRepo=new File(FILEREPO_DIR_WRITE);
		sampleNodeRepo.mkdirs();
		fetcher.setCache(new SimpleMetadataCaching(86400));
		fetcher.setHttpRetrievalEnabled(true);
        FileMetadataLoader loader = new FileMetadataLoader();
        loader.setRepositoryPath(FILEREPO_DIR_WRITE);
        fetcher.setMetadataLoaderPlugin(loader);
        fetcher.initProcessor();
		return fetcher;
	}
	
	
	@Bean
	public Properties configs() {
		return SPUtil.loadSPConfigs();
	}
	@Bean
	public CountriesRepositoryI CountriesRepository(Properties configs) {
		return new FileCountriesRepository(configs);
	}
}

