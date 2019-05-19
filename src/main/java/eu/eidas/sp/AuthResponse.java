package eu.eidas.sp;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import eu.eidas.auth.commons.EidasStringUtil;
import eu.eidas.auth.commons.attribute.AttributeDefinition;
import eu.eidas.auth.commons.attribute.AttributeValue;
import eu.eidas.auth.commons.protocol.IAuthenticationResponse;
import eu.eidas.auth.engine.ProtocolEngineFactory;
import eu.eidas.auth.engine.ProtocolEngineI;
import eu.eidas.auth.engine.configuration.dom.ProtocolEngineConfigurationFactory;
import eu.eidas.engine.exceptions.EIDASSAMLEngineException;

@Controller
public class AuthResponse {

	private static final String SAML_VALIDATION_ERROR = "Could not validate token for Saml Response";

	private final ProtocolEngineConfigurationFactory SpProtocolEngineConfigurationFactory = new ProtocolEngineConfigurationFactory(Constants.SP_SAMLENGINE_FILE, null, SPUtil.getConfigFilePath());
	private ProtocolEngineFactory SpProtocolEngineFactory;
	private ProtocolEngineI protocolEngine;

	private ImmutableMap<AttributeDefinition<?>, ImmutableSet<? extends AttributeValue<?>>> attrMap;

	@Autowired
	private Properties configs;

	@RequestMapping(value = "/AuthResponse", method = RequestMethod.POST) 
	public String response(Model model, @RequestParam String SAMLResponse, HttpServletRequest request) {
		String metadataUrl = configs.getProperty(Constants.SP_METADATA_URL);

		byte[] decSamlToken = EidasStringUtil.decodeBytesFromBase64(SAMLResponse);
		//samlResponseXML = EidasStringUtil.toString(decSamlToken);

		IAuthenticationResponse authnResponse;


		//Get SAMLEngine instance
		try {
			SpProtocolEngineFactory = new ProtocolEngineFactory(SpProtocolEngineConfigurationFactory);
			protocolEngine = SpProtocolEngineFactory.getProtocolEngine("SP");
			//ProtocolEngineI engine = SpProtocolEngineFactory.getSpProtocolEngine(SP_CONF);
			//validate SAML Token
			authnResponse = protocolEngine.unmarshallResponseAndValidate(decSamlToken, request.getRemoteHost(), 0, 0, metadataUrl,null,false);
		} catch (EIDASSAMLEngineException e) {
			if (StringUtils.isEmpty(e.getErrorDetail(	))) {
				throw new ApplicationSpecificServiceException(SAML_VALIDATION_ERROR, e.getErrorMessage());
			} else {
				throw new ApplicationSpecificServiceException(SAML_VALIDATION_ERROR, e.getErrorDetail());
			}
		}

		if (authnResponse.isFailure()) {
			throw new ApplicationSpecificServiceException("Saml Response is fail", authnResponse.getStatusMessage());
		} else {
			attrMap = authnResponse.getAttributes().getAttributeMap();
			Map<String, String> map = new HashMap<String,String>();
			for (AttributeDefinition<?> key : attrMap.keySet()){
				map.put(key.getFriendlyName(), attrMap.get(key).toString());
			}

			model.addAttribute("attrMap", map);

			//TODO: Call Workflow Manager create encounter??

			return "result"; }
	}

}
