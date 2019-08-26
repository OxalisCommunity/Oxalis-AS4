# Oxalis-AS4

This is a release candidate of Oxalis with support for PEPPOL AS4 pMode.
It supports Oxalis v4.0.4 and passes the CEF conformance test (excluding tests requiring multiple payloads).

AS4 messages is triggered by setting the _transport profile identifier_ of one of your endpoints to "_peppol-transport-as4-v2_0_" in the SMP. No further configuration is needed beyond the standard Oxalis setup.
For general instructions on how to install and use Oxalis, please refer to [oxalis installation guide](https://github.com/difi/oxalis/blob/master/doc/installation.md).

## Installation guide

We will be basing this installation guide on Oxalis _4.0.2_. Specifically _Oxalis Server_ ([download](http://central.maven.org/maven2/no/difi/oxalis/oxalis-server/4.0.2/)) for inbound traffic and _Oxalis Standalone_ ([download](http://central.maven.org/maven2/no/difi/oxalis/oxalis-standalone/4.0.2/)) for outbound traffic. The same approach will work for all the other components of Oxalis 4.x

### Install Oxalis Inbound (Server)

Oxalis server comes out of the box with a folder for extensions (named "ext"). Extract the content of _oxalis-as4-4.1.0-SNAPSHOT-dist.zip_ into this folder. No further configuration is needed.

Start Oxalis server in the normal way, either trough _run.sh_ or _run.bat_. 

The easiest way to see that the AS4 endpoint is up and running is to visit its endpoint address.
If we now visit ``localhost:8080/as4`` we will be greeted  with the message ``Hello AS4 world``

### Install Oxalis Inbound (Tomcat 8+)

We have added an example deployment XML file for tomcat 8+. Ensure that your file locations match with the paths in the XML.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!-- For a default Tomcat setup, name this file 'oxalis.xml' and place it in the following directory -->
<!-- $CATALINA_BASE/conf/Catalina/localhost/ -->
<!-- this will expose the as4 endpoint on http://localhoist:8080/oxalis/as4 -->


<!-- The Context element represents our application. -->
<!-- The 'docBase' attribute will define the main application to be run. -->
<!-- For more information see https://tomcat.apache.org/tomcat-8.5-doc/config/context.html -->
<Context docBase="C:\dev\GitHub\oxalis\target\distribution\jee\oxalis.war"  >
    
   <!-- Optionally define the OXALIS_HOME environment variable  -->
   <!--
   <Environment name="OXALIS_HOME" value="/path/to/oxalis/config/directory" type="java.lang.String" override="false" />
   -->

   <!-- Here we can add a list of additional resources to add to the application --> 
   <!-- For more information see https://tomcat.apache.org/tomcat-8.5-doc/config/resources.html -->
   <Resources className="org.apache.catalina.webresources.ExtractingRoot">
            
      <!-- Add the other dependencies of oxalis -->
      <!--
      <PreResources 
          base="/path/to/oxalis/dir" 
          className="org.apache.catalina.webresources.DirResourceSet"
          webAppMount="/WEB-INF/lib"
          readOnly="true" />
      --> 
      
      <!-- Add this resource after (this resourse wil override files found in 'Context') the one we defined in the context element -->
      <PostResources
        base="C:\GitHub-releases\Oxalis-AS4\oxalis-as4-4.1.0-SNAPSHOT-dist\" 
        className="org.apache.catalina.webresources.DirResourceSet"
        webAppMount="/WEB-INF/lib"
        />  
      
      <!-- The name 'PreResources' and 'PostResources' refers to whether the resources will be added before or after the resource we defined in the 'Context' element -->
      <!-- There is also 'JarResources' that will let us alter the content of the 'Context' resource itself -->
      <!-- the precedence only affects witch version to use in case multiple resources provide the same content (the latest element will win) -->
      
   </Resources>

</Context>
```

### Install Oxalis Outbound (Standalone)

Oxalis SimpleSender does not come with an extension folder. So we need to add the extension logic that where define in the _run_ scripts our self.

We make a base folder named oxalis-standalone-as4 with two sub folders:
<pre>
oxalis-standalone-as4/    <-- Base folder, we will run our commands from here
├── standalone/    <-- We will putt our regular <em>Oxalis Standalone</em> application here...
│   ├── oxalis-standalone.jar
│   ├── posibly-other.jar
│   └── ...
└── as4/    <-- ...and our <em>AS4</em> extension here
    ├── oxalis-as4.jar
    ├── many-other.jar
    └── ...
</pre>

To run our combined application all we need to do is to run the following command (This command assumes we are standing in our base folder):
<pre>
java -classpath "standalone/*;as4/*" eu.sendregning.oxalis.Main [followd by the argument like -f c:\some-invoice.xml]
</pre>

All this command does is to tell Java to load the content of both folders, then execute the logic in "_eu.sendregning.oxalis.Main_" (which is the starting point of the Standalone application).
By looking into the run scripts of Oxalis Server form our previous section we can see that this is in fact the same approach that is used there.

We can see from the start up log that the AS4 v2 transportation profile is loaded together with the v1 we added in our configuration previously.
``[no.difi.oxalis.outbound.transmission.MessageSenderFactory] => bdxr-transport-ebms3-as4-v1p0``
``[no.difi.oxalis.outbound.transmission.MessageSenderFactory] => peppol-transport-as4-v2_0``

## Other

* [CEF-Connectivity guide](docs/CEF-connectivity.md) (RC-7 onwards)
