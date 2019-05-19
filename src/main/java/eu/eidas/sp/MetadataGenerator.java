package eu.eidas.sp;

import java.security.cert.X509Certificate;
import java.util.Properties;

import org.joda.time.DateTime;
import org.joda.time.DurationFieldType;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import eu.eidas.auth.commons.EIDASValues;
import eu.eidas.auth.engine.ProtocolEngineFactory;
import eu.eidas.auth.engine.ProtocolEngineI;
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

@Controller
public class MetadataGenerator {
	
	@Autowired
	Properties configs;

	private ProtocolEngineFactory SpProtocolEngineFactory;
	private ProtocolEngineConfigurationFactory SpProtocolEngineConfigurationFactory;
	private long validityDuration;
	
    private ProtocolEngineI setSPMetadataRoleParams(final EidasMetadataParametersI emp, final EidasMetadataRoleParametersI emrp) throws EIDASSAMLEngineException {
        emrp.setRole(MetadataRole.SP);
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
    
    private DateTime metadataValidityDurationDuration(int seconds, ProtocolEngineI protocolEngine) {
        DateTime expiryDate = protocolEngine.getClock().getCurrentTime();
        return expiryDate.withFieldAdded(DurationFieldType.seconds(), (int) (validityDuration));
    }
    
	@RequestMapping(value = "/metadata", method = RequestMethod.GET, produces = { "application/xml", "text/xml" }, consumes = MediaType.ALL_VALUE )
	@ResponseBody
	public String generateMetadata(){
		String metadata="invalid metadata";
		if(SPUtil.isMetadataEnabled()) {
			try {
				EidasMetadataParametersI emp = MetadataConfiguration.newParametersInstance();
                EidasMetadataRoleParametersI emrp = MetadataConfiguration.newRoleParametersInstance();

                final ProtocolEngineI protocolEngine;
                protocolEngine = setSPMetadataRoleParams(emp, emrp);
                emrp.setEncryptionAlgorithms(configs == null ? null : configs.getProperty(EncryptionKey.ENCRYPTION_ALGORITHM_WHITE_LIST.getKey()));
                emrp.setWantAssertionsSigned(true);
                emp.setEntityID(configs.getProperty(Constants.SP_METADATA_URL));
                emp.setAssuranceLevel(null);
                emp.addRoleDescriptor(emrp);
                emp.setSigningMethods(configs == null ? null : configs.getProperty(SignatureKey.SIGNATURE_ALGORITHM_WHITE_LIST.getKey()));
                emp.setDigestMethods(configs == null ? null : configs.getProperty(SignatureKey.SIGNATURE_ALGORITHM_WHITE_LIST.getKey()));
                emp.setHideLoaType(true);
                emp.setValidUntil(metadataValidityDurationDuration(18000, protocolEngine));
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

            } catch (EIDASMetadataException eidasSamlexc) {
                System.out.println("ERROR : Error creating Node metadata " + eidasSamlexc.getMessage());
              

            } catch (EIDASSAMLEngineException eidasSamlexc) {
            	System.out.println("ERROR : Error creating Node metadata " + eidasSamlexc.getMessage());
            	

            }
		}
		//dataStream = new ByteArrayInputStream(EidasStringUtil.getBytes(metadata));
		return metadata;
	}
}
