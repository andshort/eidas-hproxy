package eu.eidas.sp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.joda.time.DateTime;
import org.joda.time.DurationFieldType;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import eu.eidas.auth.commons.EIDASValues;
import eu.eidas.auth.commons.EidasStringUtil;
import eu.eidas.auth.engine.DefaultProtocolEngineFactory;
import eu.eidas.auth.engine.ProtocolEngineFactory;
import eu.eidas.auth.engine.ProtocolEngineI;
import eu.eidas.auth.engine.configuration.dom.DOMConfigurationParser;
import eu.eidas.auth.engine.configuration.dom.EncryptionKey;
import eu.eidas.auth.engine.configuration.dom.ProtocolEngineConfigurationFactory;
import eu.eidas.auth.engine.configuration.dom.SignatureKey;
import eu.eidas.auth.engine.metadata.ContactData;
import eu.eidas.auth.engine.metadata.EidasMetadata;
import eu.eidas.auth.engine.metadata.EidasMetadataParametersI;
import eu.eidas.auth.engine.metadata.EidasMetadataRoleParametersI;
import eu.eidas.auth.engine.metadata.MetadataConfiguration;
import eu.eidas.auth.engine.metadata.MetadataSignerI;
import eu.eidas.auth.engine.metadata.OrganizationData;
import eu.eidas.auth.engine.metadata.impl.MetadataRole;
import eu.eidas.engine.exceptions.EIDASMetadataException;
import eu.eidas.engine.exceptions.EIDASSAMLEngineException;
import eu.eidas.sp.MetadataUtil;
import eu.eidas.sp.Constants;
import eu.eidas.sp.SPUtil;
//import eu.eidas.sp.nextNode;
//import eu.eidas.sp.eidasHProxyRequestAuth;
//import eu.eidas.sp.eidasHProxyAuthResponse;
@Controller
public class MetadataGenerator {
	
	@Autowired
	Properties configs;
	//private transient InputStream dataStream;
	private ProtocolEngineFactory SpProtocolEngineFactory;
	private ProtocolEngineConfigurationFactory SpProtocolEngineConfigurationFactory;
	private long validityDuration;
	

    private ProtocolEngineI setSPMetadataRoleParams(final EidasMetadataParametersI emp, final EidasMetadataRoleParametersI emrp) throws EIDASSAMLEngineException {
        emrp.setRole(MetadataRole.SP);

        //TODO these bindings should come from configuration
        emrp.setDefaultBinding(SAMLConstants.SAML2_POST_BINDING_URI);
        emrp.addProtocolBindingLocation(SAMLConstants.SAML2_POST_BINDING_URI, configs.getProperty(Constants.SP_RETURN));
        emrp.addProtocolBindingLocation(SAMLConstants.SAML2_REDIRECT_BINDING_URI, configs.getProperty(Constants.SP_RETURN));

        emp.setSpType(configs.getProperty(Constants.SP_TYPE, null));
        
        SpProtocolEngineConfigurationFactory = new ProtocolEngineConfigurationFactory(Constants.SP_SAMLENGINE_FILE, null, SPUtil.getConfigFilePath());
        SpProtocolEngineFactory = new ProtocolEngineFactory(SpProtocolEngineConfigurationFactory);
        
        final ProtocolEngineI protocolEngine = SpProtocolEngineFactory.getProtocolEngine("SP");
        final X509Certificate spEngineDecryptionCertificate = protocolEngine.getDecryptionCertificate();
        if (spEngineDecryptionCertificate != null) {
            emrp.setEncryptionCertificate(spEngineDecryptionCertificate);
        }

        emrp.setSigningCertificate(protocolEngine.getSigningCertificate());
        return protocolEngine;
    }
    
    private void setValidityUntil(EidasMetadataParametersI emp, ProtocolEngineI protocolEngine) {
        DateTime expiryDate = protocolEngine.getClock().getCurrentTime();
        expiryDate = expiryDate.withFieldAdded(DurationFieldType.seconds(), (int) (validityDuration));
        emp.setValidUntil(expiryDate);
    }
    
	@RequestMapping(value = "/metadata", method = RequestMethod.GET, produces = { "application/xml", "text/xml" }, consumes = MediaType.ALL_VALUE )
	@ResponseBody
	public String generateMetadata(){



		String metadata="invalid metadata";
		if(SPUtil.isMetadataEnabled()) {
			try {
				EidasMetadataParametersI emp = MetadataConfiguration.newParametersInstance();
                EidasMetadataRoleParametersI emrp = MetadataConfiguration.newRoleParametersInstance();

				//MetadataConfigParams.Builder mcp = MetadataConfigParams.builder();
                final ProtocolEngineI protocolEngine;
                protocolEngine = setSPMetadataRoleParams(emp, emrp);
                emrp.setEncryptionAlgorithms(configs == null ? null : configs.getProperty(EncryptionKey.ENCRYPTION_ALGORITHM_WHITE_LIST.getKey()));
                emrp.setWantAssertionsSigned(true);
                emp.setEntityID(configs.getProperty(Constants.SP_METADATA_URL));
                emp.setAssuranceLevel(null);
                emp.addRoleDescriptor(emrp);
                
                //emp.setValidUntil(new DateTime().plusDays(10));
                
                emp.setSigningMethods(configs == null ? null : configs.getProperty(SignatureKey.SIGNATURE_ALGORITHM_WHITE_LIST.getKey()));
                emp.setDigestMethods(configs == null ? null : configs.getProperty(SignatureKey.SIGNATURE_ALGORITHM_WHITE_LIST.getKey()));
                emp.setHideLoaType(true);
                validityDuration = 18000;
                setValidityUntil(emp, protocolEngine);
                
                ContactData technicalContact = MetadataUtil.createConnectorTechnicalContact(configs);
                ContactData supportContact = MetadataUtil.createConnectorSupportContact(configs);
                OrganizationData organization = MetadataUtil.createConnectorOrganizationData(configs);
          
                
                emp.setTechnicalContact(technicalContact);
                emp.setSupportContact(supportContact);
                emp.setOrganization(organization);

                emp.setEidasProtocolVersion(configs == null ? null : configs.getProperty(EIDASValues.EIDAS_PROTOCOL_VERSION.toString()));
                emp.setEidasApplicationIdentifier(configs == null ? null : configs.getProperty(EIDASValues.EIDAS_APPLICATION_IDENTIFIER.toString()));

                
                EidasMetadata.Generator generator = EidasMetadata.generator(emp);
                EidasMetadata eidasMetadata = generator.generate((MetadataSignerI) protocolEngine.getSigner());

                metadata = eidasMetadata.getMetadata();
                
                
//				mcp.spEngine(SpProtocolEngineFactory.getSpProtocolEngine(SP_CONF));
//				mcp.entityID(configs.getProperty(Constants.SP_METADATA_URL));
//				String returnUrl = configs.getProperty(Constants.SP_RETURN);
//				mcp.assertionConsumerUrl(returnUrl);
//				mcp.technicalContact(MetadataUtil.createTechnicalContact(configs));
//				mcp.supportContact(MetadataUtil.createSupportContact(configs));
//				mcp.organization(MetadataUtil.createOrganization(configs));
//				mcp.signingMethods(configs == null ? null : configs.getProperty(SignatureKey.SIGNATURE_ALGORITHM_WHITE_LIST.getKey()));
//				mcp.digestMethods(configs == null ? null : configs.getProperty(SignatureKey.SIGNATURE_ALGORITHM_WHITE_LIST.getKey()));
//				mcp.encryptionAlgorithms(configs == null ? null : configs.getProperty(EncryptionKey.ENCRYPTION_ALGORITHM_WHITE_LIST.getKey()));
//				String spType =  configs.getProperty(Constants.SP_TYPE, null);
//				mcp.spType(StringUtils.isBlank(spType) ? null : spType);
//				mcp.eidasProtocolVersion(configs == null ? null : configs.getProperty(EIDASValues.EIDAS_PROTOCOL_VERSION.toString()));
//				mcp.eidasApplicationIdentifier(configs == null ? null : configs.getProperty(EIDASValues.EIDAS_APPLICATION_IDENTIFIER.toString()));
//				generator.configParams(mcp.build());
//				metadata = generator.build().getMetadata();
            } catch (EIDASMetadataException eidasSamlexc) {
                System.out.println("ERROR : Error creating Node metadata " + eidasSamlexc.getMessage());
              

            } catch (EIDASSAMLEngineException eidasSamlexc) {
            	System.out.println("ERROR : Error creating Node metadata " + eidasSamlexc.getMessage());
            	

            }
		}
		//dataStream = new ByteArrayInputStream(EidasStringUtil.getBytes(metadata));
		return metadata;
	}

	
	/*
	 * //Initiate auth from Patient U/I e.g. /auth?citizen=CA or /auth if national
	 * adapter is used
	 * 
	 * @GetMapping("/auth") public ModelAndView
	 * startauth(@RequestParam(value="citizen", required=false, defaultValue="NA")
	 * String citizen) {
	 * 
	 * eidasHProxyRequestAuth eidasHProxyRequestAuth = new eidasHProxyRequestAuth();
	 * nextNode nextNode = eidasHProxyRequestAuth.startauth(citizen);
	 * 
	 * ModelAndView model = new ModelAndView("nextnode"); model.addObject(nextNode);
	 * return model; }
	 */

	
	/*
	 * @RequestMapping(value = "/AuthResponse", method = RequestMethod.POST) public
	 * String response(Model model, @RequestParam String SAMLResponse,
	 * HttpServletRequest request) { eidasHProxyAuthResponse eidasHProxyAuthResponse
	 * = new eidasHProxyAuthResponse(); Map<String, String> map; map =
	 * eidasHProxyAuthResponse.response(SAMLResponse, request.getRemoteHost());
	 * model.addAttribute("attrMap", map);
	 * 
	 * //TODO: Call Workflow Manager create encounter??
	 * 
	 * return "result"; }
	 */
}
