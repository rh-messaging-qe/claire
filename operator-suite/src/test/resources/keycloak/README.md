### User Federation using LDAP
* READ_ONLY mode (writable will/can remove/alter users in ldap itself)
* Vendor `Active Directory` (but RHDS should work as well)
* Username LDAP attribute - `uid`
* RDN LDAP attribute  - `uid`
* UUID LDAP attribute - `uid`
* User Object Classes - `inetOrgPerson`
* Connection URL - `ldap://openldap:1389`
* Users DN - `ou=users,dc=example,dc=org`
* Bind type - `simple`
* Bind DN - `cn=admin,dc=example,dc=org`
* Bind Credential - `admin`

Update `Mappers` if needed
* username - `uid`
* firstName - `cn`
* lastName - `sn`

See `users.ldif` for more details of LDAP configuration (possibly update in future).


### LDAP Roles mapping
Use `role-ldap-mapper` to import LDAP provided user groups on to LDAP imported users.
Search for `role-ldap-mapper` to get more help on this
`group-ldap-mapper` created keycloak groups, but does not assign group-role matching in clientId.

### AMQ Broker JAAS configuration to Keycloak
For actual setup of Keycloak Artemis integration see `KeycloakLdapTests` code.
```
console {
    // ensure the operator can connect to the broker by referencing the existing properties config
    org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule sufficient
        org.apache.activemq.jaas.properties.user="artemis-users.properties"
        org.apache.activemq.jaas.properties.role="artemis-roles.properties"
        baseDir="/home/jboss/amq-broker/etc";

   org.keycloak.adapters.jaas.BearerTokenLoginModule sufficient
       keycloak-config-file="${secret.mount}/_keycloak-bearer-token.json"
       role-principal-class=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal
   ;
};
activemq {
   org.keycloak.adapters.jaas.DirectAccessGrantsLoginModule required
       keycloak-config-file="${secret.mount}/_keycloak-direct-access.json"
       role-principal-class=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal
   ;
   org.apache.activemq.artemis.spi.core.security.jaas.PrincipalConversionLoginModule required
       principalClassList=org.keycloak.KeycloakPrincipal
   ;
};
```
[keycloak-bearer-token.json]
```json
{
  "realm": "<my-realm>",
  "resource": "amq-console",
  "auth-server-url": "<keycloak-auth-url>",
  "principal-attribute": "preferred_username",
  "use-resource-role-mappings": false,
  "ssl-required": "external",
  "confidential-port": 0
}
```

[keycloak-direct-access.json]
```json
{
  "realm": "<my-realm>",
  "resource": "amq-broker",
  "auth-server-url": "<keycloak-auth-url>",
  "principal-attribute": "preferred_username",
  "use-resource-role-mappings": false,
  "ssl-required": "external",
  "credentials": {
    "secret": "<clientid_secret>"
  }
}
```
[keycloak-js-client.json
]
```json
{
  "realm": "<my-realm>",
  "clientId": "amq-console",
  "url": "<keycloak-auth-url>"
}
```

For more details see [security-keycloak](https://github.com/apache/activemq-artemis/tree/main/examples/features/standard/security-keycloak) example in ActiveMQ Artemis project.
