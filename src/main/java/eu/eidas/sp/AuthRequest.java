package eu.eidas.sp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.google.common.collect.ImmutableSortedSet;

import eu.eidas.auth.commons.EidasStringUtil;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.ImmutableAttributeMap;
import eu.eidas.auth.commons.protocol.IRequestMessage;
import eu.eidas.auth.commons.protocol.eidas.LevelOfAssurance;
import eu.eidas.auth.commons.protocol.eidas.LevelOfAssuranceComparison;
import eu.eidas.auth.commons.protocol.eidas.SpType;
import eu.eidas.auth.commons.protocol.eidas.impl.EidasAuthenticationRequest;
import eu.eidas.auth.commons.protocol.impl.EidasSamlBinding;
import eu.eidas.auth.commons.protocol.impl.SamlBindingUri;
import eu.eidas.auth.engine.ProtocolEngineFactory;
import eu.eidas.auth.engine.ProtocolEngineI;
import eu.eidas.auth.engine.SamlEngineSystemClock;
import eu.eidas.auth.engine.configuration.dom.ProtocolEngineConfigurationFactory;
import eu.eidas.auth.engine.metadata.EidasMetadataParametersI;
import eu.eidas.auth.engine.metadata.EidasMetadataRoleParametersI;
import eu.eidas.auth.engine.metadata.MetadataFetcherI;
import eu.eidas.auth.engine.metadata.MetadataSignerI;
import eu.eidas.auth.engine.metadata.MetadataUtil;
import eu.eidas.auth.engine.xml.opensaml.SAMLEngineUtils;
import eu.eidas.engine.exceptions.EIDASMetadataException;
import eu.eidas.engine.exceptions.EIDASSAMLEngineException;

@Controller
public class AuthRequest {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuthRequest.class);

	private final ProtocolEngineConfigurationFactory SpProtocolEngineConfigurationFactory = new ProtocolEngineConfigurationFactory(Constants.SP_SAMLENGINE_FILE, null, SPUtil.getConfigFilePath());
	private ProtocolEngineFactory SpProtocolEngineFactory;

	private ProtocolEngineI protocolEngine;

	private String samlRequest;

	private String nodeMetadataUrl;

	private String nodeUrl;

	@Autowired
	private MetadataFetcherI fetcher;

	@Autowired
	private Properties configs;

	@Autowired
	private CountriesRepositoryI countries;

	private void error(String title, String message) {
		throw new ApplicationSpecificServiceException(title, message);
	}

	private String getSSOSLocation(String metadataUrl, SamlBindingUri binding) throws EIDASSAMLEngineException {
		SpProtocolEngineFactory = new ProtocolEngineFactory(SpProtocolEngineConfigurationFactory);
		protocolEngine = SpProtocolEngineFactory.getProtocolEngine("SP");
		MetadataSignerI metadataSigner = (MetadataSignerI) protocolEngine.getSigner();
		EidasMetadataParametersI EidasMetadataParameters;
		try {
			EidasMetadataParameters = fetcher.getEidasMetadata(metadataUrl, metadataSigner, new SamlEngineSystemClock());
			EidasMetadataRoleParametersI EidasMetadataRoleParameters = MetadataUtil.getIDPRoleDescriptor(EidasMetadataParameters);
			Map<String,String> ProtocolBindings = EidasMetadataRoleParameters.getProtocolBindingLocations();

			return ProtocolBindings.get("POST");

		} catch (EIDASMetadataException e) {
			LOGGER.info("Unable to get Eidas Metadata for: " + metadataUrl);
			e.printStackTrace();
			return null;
		}
	}

	private ImmutableAttributeMap RequestedAttributes() {
		final ImmutableSortedSet<AttributeDefinition<?>> allSupportedAttributesSet = protocolEngine.getProtocolProcessor().getAllSupportedAttributes();
		List<AttributeDefinition<?>> reqAttrList = new ArrayList<AttributeDefinition<?>>(allSupportedAttributesSet);
		List<String> reqNameUri = Arrays.asList("http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName" , "http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName", "http://eidas.europa.eu/attributes/naturalperson/DateOfBirth", "http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier");

		for (AttributeDefinition<?> attributeDefinition : allSupportedAttributesSet) {
			if (!reqNameUri.contains(attributeDefinition.getNameUri().toString()))
				reqAttrList.remove(attributeDefinition);
		}
		return new ImmutableAttributeMap.Builder().putAll(reqAttrList).build();
	}

	@GetMapping("/auth")
	public ModelAndView startauth(@RequestParam(value="citizen", required=false, defaultValue="NA") String citizen) {

		String countryMetadataUrl = countries.getCountryMetadataUrl(citizen);
		if (countryMetadataUrl.equals("")) {
			error("Country metadata not found", citizen + " not in configuration");
		}

		try {
			nodeUrl = getSSOSLocation(countryMetadataUrl, SamlBindingUri.SAML2_POST);
		} catch (EIDASSAMLEngineException e) {
			LOGGER.error("Unable to load SSOSLocation from metadata");
			e.printStackTrace();
		}

		EidasAuthenticationRequest.Builder reqBuilder = new EidasAuthenticationRequest.Builder();

		reqBuilder.destination(countryMetadataUrl);
		reqBuilder.providerName(configs.getProperty("provider.name"));
		reqBuilder.requestedAttributes(RequestedAttributes());
		reqBuilder.levelOfAssurance(LevelOfAssurance.LOW.stringValue());
		reqBuilder.spType(SpType.PUBLIC.toString());
		reqBuilder.levelOfAssuranceComparison(LevelOfAssuranceComparison.fromString("minimum").stringValue());
		reqBuilder.nameIdFormat("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");
		reqBuilder.binding(EidasSamlBinding.EMPTY.getName());

		String metadataUrl = configs.getProperty(Constants.SP_METADATA_URL);
		if (metadataUrl != null && !metadataUrl.isEmpty() && SPUtil.isMetadataEnabled()) {
			reqBuilder.issuer(metadataUrl);
		}

		reqBuilder.serviceProviderCountryCode(configs.getProperty("sp.country"));
		reqBuilder.citizenCountryCode(citizen);

		IRequestMessage binaryRequestMessage;

		try {
			// TODO quick fix for having a flow working end-to-end check if this is correct:
			// generated missing id
			reqBuilder.id(SAMLEngineUtils.generateNCName());
			binaryRequestMessage = protocolEngine.generateRequestMessage(reqBuilder.build(), nodeMetadataUrl);
		} catch (EIDASSAMLEngineException e) {
			LOGGER.error(e.getMessage());
			LOGGER.error("", e);
			throw new ApplicationSpecificServiceException("Could not generate token for Saml Request", e.getMessage());
		}

		byte[] token = binaryRequestMessage.getMessageBytes();
		samlRequest = EidasStringUtil.encodeToBase64(token);
		nextNode nextNode = new nextNode();
		nextNode.redirectUri = nodeUrl;
		nextNode.postLocationUrl = nodeUrl;
		nextNode.redirectLocationUrl = nodeUrl;
		nextNode.country = citizen;
		nextNode.RelayState = "MyRelayState";
		nextNode.SAMLRequest = samlRequest;
		nextNode.SAMLPlain = EidasStringUtil.toString(token);
		ModelAndView model = new ModelAndView("nextnode"); 
		model.addObject("debug", true);
		model.addObject(nextNode);
		return model;

	}
}
