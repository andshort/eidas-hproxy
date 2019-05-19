Prerequisites

1) Download EIDAS v2.2 sources
2) cd EIDAS-Parent
3) build eidas-saml-engine (mvn clean install -pl :eidas-saml-engine -am)
4) Set environmental variable for configuration location (path to sp.properties file e.g. "export eidas_config=/path/to/file/")