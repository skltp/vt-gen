#!/usr/bin/env groovy

package se.skltpservices.tools

import groovy.io.FileType

org.apache.commons.io.FileUtils

@Grab(group='commons-io', module='commons-io', version='1.3.2')
import org.apache.commons.io.FileUtils

@Grab(group='dom4j', module='dom4j', version='1.6.1')
import org.dom4j.io.SAXReader

/**
 * This script helps us to generate many services at one time.
 *
 * PREREQUISITES:
 * This script is depending on the archetype named service-archetype.
 * This archetype must be installed before running the script.
 * Check the TODO below to see any flaws still not fixed.
 *
 * TO RUN:
 * Just execute ./VirtualiseringGenerator.groovy and follow the instruction coming up.
 *
 * Version info:
 * A first version is created to solve that we would like to generate several service interactions without to much manual work.
 *
 * TODO:
 *
 */

def getAllFilesMatching(direcory, pattern){
	def filesFound = []
	direcory?.traverse(type:FileType.FILES, nameFilter: ~pattern){ fileFound -> filesFound << fileFound }
	filesFound.each { fileFound -> println "File to process: ${fileFound.name}" }
	return filesFound
}

def getAllDirectoriesMatching(direcory, pattern){
	def dirsFound = []
	direcory?.traverse(type:FileType.DIRECTORIES, nameFilter: ~pattern){ dirFound -> dirsFound << dirFound }
	dirsFound.each { dirFound -> println "Directory to process: ${dirFound}" }
	return dirsFound
}

def getAllUniqueRivNameSpaces(wsdlFile){
	def rivNameSpace = 'No namespace found'

	new SAXReader().read(wsdlFile).getRootElement().declaredNamespaces().grep(~/.*urn:riv.*/).each{ namespace ->
		if(namespace.text.contains('rivtabp')){
			rivNameSpace = namespace.text
		}
	}
	return rivNameSpace
}

def getServiceContractNameSpace(xsdFile){
	def featureKeepAliveServiceContractNameSpace = 'No servicecontract namespace found'

	//Build keep alive feature settings "feature.keepalive.servicecontractnamespace" using . (dots) instead of : (colons)
	//Ignore minor version number when coming to feature settings
	new SAXReader().read(xsdFile).getRootElement().declaredNamespaces().grep(~/.*urn:riv.*/).each{ namespace ->
		if(namespace.text.contains('Responder') &! namespace.text.contains('.')){
			featureKeepAliveServiceContractNameSpace = namespace.text.replaceAll(':', '.')
		}
	}
	return featureKeepAliveServiceContractNameSpace
}

def checkIfVersionNumbersInNameSpaces(serviceInteractionDirectories) {
	def found = false
	serviceInteractionDirectories.each { serviceInteractionDirectory ->
		def xsdFiles = getAllFilesMatching(serviceInteractionDirectory, /.*\.xsd/)
		// Check that namespace has a 1-digit version number, if not fail!
		def version = getServiceContractNameSpaceVersion(xsdFiles[0])
		if (version.indexOf('.') > 0 ) {
			found = true
			println "Incorrect version number for the following service contract! " + serviceInteractionDirectory.toString() + ". Version is: " + version 
		}
	}
	return found
}

def getServiceContractNameSpaceVersion(xsdFile){
	def featureServiceContractNameSpaceVersion= ''

	new SAXReader().read(xsdFile).getRootElement().declaredNamespaces().grep(~/.*urn:riv.*/).each{ namespace ->
		if(namespace.text.contains('Responder')){
			featureServiceContractNameSpaceVersion = namespace.text.substring(namespace.text.lastIndexOf(':')+1, namespace.text.length())
		}
	}
	return featureServiceContractNameSpaceVersion
}


def getServiceContractVersion(xsdFile){
	def serviceContractVersion = 'No service contract version found'

	//Read version from root element
	serviceContractVersion = new SAXReader().read(xsdFile).getRootElement().attributeValue("version")
	return serviceContractVersion
}

def buildVirtualServices(serviceInteractionDirectories, targetDir){

	serviceInteractionDirectories.each { serviceInteractionDirectory ->

		def (name, schemaDir) = serviceInteractionDirectory.parent.split("schemas")
		def artifactId = serviceInteractionDirectory.name - 'Interaction'

		def wsdlFiles = getAllFilesMatching(serviceInteractionDirectory, /.*\.wsdl/)
		def xsdFiles = getAllFilesMatching(serviceInteractionDirectory, /.*\.xsd/)

		def serviceInteractionNameSpace = getAllUniqueRivNameSpaces(wsdlFiles[0])
		def serviceNameSpaceArray = serviceInteractionNameSpace.split("\\:")

		def namespacePrefix = serviceNameSpaceArray[0] + ":" + serviceNameSpaceArray[1]
		def maindomain = serviceNameSpaceArray[2]

		def serviceNameSpaceSize=serviceNameSpaceArray.size()-1
		def rivtaVersion = serviceNameSpaceArray[serviceNameSpaceSize]
		def serviceVersion = serviceNameSpaceArray[serviceNameSpaceSize-1]
		def serviceName = serviceNameSpaceArray[serviceNameSpaceSize-2]
		def subdomain=serviceNameSpaceArray[3]

		def i=serviceNameSpaceSize-3
		for (def y=4; i>=y;y++) {
			subdomain = subdomain + ":" + serviceNameSpaceArray[y]
		}
		def subdomainAdress = subdomain.replaceAll(':', '/')
		def subdomainFlow = subdomain.replaceAll(':', '-')
		def subdomainGroupId = subdomain.replaceAll(':', '.')

		def serviceRelativePath = "$artifactId/$serviceVersion/$rivtaVersion"
		def wsdlFileName = wsdlFiles[0].name

		//Version of the service contract e.g Tidbokning 1.1.0
    	def virtualizationVersion = '1.0-SNAPSHOT'

		def serviceContractNameSpace = getServiceContractNameSpace(xsdFiles[0])
		def serviceContractVersion = getServiceContractVersion(xsdFiles[0])
		
		//För tjänster som skall ha subdomän i ändpunktens adressen eller inte
		//-DhttpsEndpointAdress=https://\${TP_HOST}:\${TP_PORT}/\${TP_BASE_URI}/$maindomain/$subdomainAdress/$serviceRelativePath
		//-DhttpEndpointAdress=http://\${TP_HOST}:\${TP_PORT_HTTP}/\${TP_BASE_URI}/$maindomain/$subdomainAdress/$serviceRelativePath
		//-DhttpsEndpointAdress=https://\${TP_HOST}:\${TP_PORT}/\${TP_BASE_URI}/$serviceRelativePath
		//-DhttpEndpointAdress=http://\${TP_HOST}:\${TP_PORT_HTTP}/\${TP_BASE_URI}/$serviceRelativePath

		//För tjänster som skall ha möjlighet att ställa in response timeout per tjänstedomän
		//-DfeatureResponseTimeoutValue=\${feature.featureresponsetimeout.${maindomain}.${subdomain}:\${SERVICE_TIMEOUT_MS}}

		def mvnCommand = """mvn archetype:generate
		-DinteractiveMode=false
		-DarchetypeArtifactId=virtualServiceArchetype
		-DarchetypeGroupId=se.skltp.virtualservices.tools
		-DarchetypeVersion=1.5-SNAPSHOT
		-Duser.dir=${targetDir}
		-DgroupId=se.skltp.virtualservices.${maindomain}.${subdomainGroupId}
		-DartifactId=${artifactId}
		-Dversion=${virtualizationVersion}
		-DvirtualiseringArtifactId=${maindomain}-${subdomainFlow}-${serviceContractVersion}-${artifactId}-virtualisering
    	-DhttpsEndpointAdress=https://\${TP_HOST}:\${TP_PORT}/\${TP_BASE_URI}/$maindomain/$subdomainAdress/$serviceRelativePath
		-DhttpEndpointAdress=http://\${TP_HOST}:\${TP_PORT_HTTP}/\${TP_BASE_URI}/$maindomain/$subdomainAdress/$serviceRelativePath
		-DflowName=${maindomain}-${subdomainFlow}-${artifactId}-${serviceContractVersion}-Interaction-virtualisering-flow
		-DfeatureKeepaliveValue=\${feature.keepalive.${serviceContractNameSpace}:\${feature.keepalive}}
		-DfeatureResponseTimeoutValue=\${feature.featureresponsetimeout.${serviceContractNameSpace}:\${SERVICE_TIMEOUT_MS}}
		-DserviceMethod=${artifactId}
		-DserviceWsdlFileDir=classpath:/schemas$schemaDir/${artifactId}Interaction/${wsdlFileName}
		-DserviceNamespace=${serviceInteractionNameSpace}
    	-DwsdlServiceName=${artifactId}ResponderService"
		"""
		println "$mvnCommand"

		def process = mvnCommand.execute()
		process.waitFor()

		// Obtain status and output
		println "RETURN CODE: ${ process.exitValue()}"
		println "STDOUT: ${process.in.text}"
	}
}

def copyServiceSchemas(serviceInteractionDirectories, targetDir){
	serviceInteractionDirectories.each { serviceInteractionDirectory ->
		def schemasFiles = getAllFilesMatching(serviceInteractionDirectory, /.*\.xsd|.*\.xml|.*\.wsdl/)

		def (name, parentDir) = serviceInteractionDirectory.parent.split("schemas")

		def serviceInteraction = serviceInteractionDirectory.name
		def serviceDirectory = serviceInteraction - 'Interaction'
		def schemaTargetDir = "${targetDir}/${serviceDirectory}/src/main/resources/schemas/$parentDir/${serviceInteraction}"
		new File("${schemaTargetDir}").mkdirs()

		schemasFiles.each {sourceSchemaFile ->
			def targetSchemaFile = new File("${schemaTargetDir}/$sourceSchemaFile.name")
			FileUtils.copyFile(sourceSchemaFile, targetSchemaFile)}

		def interactionsDirectory = serviceInteractionDirectory.parent
		def interactionsDir = new File("${interactionsDirectory}")
		def sourceSubSchemaFile = null
		interactionsDir?.traverse(maxDepth:0,type:FileType.FILES, nameFilter: ~/.*\.xsd$/) {  file ->
		 	sourceSubSchemaFile = file
	    }
	    if (sourceSubSchemaFile != null) {
			def subSchemaFileTargetDir = "${targetDir}/${serviceDirectory}/src/main/resources/schemas/$parentDir/"
			def targetSubSchemaFile = new File("${subSchemaFileTargetDir}/${sourceSubSchemaFile.name}")
			FileUtils.copyFile(sourceSubSchemaFile, targetSubSchemaFile)
		}
	}
}

def copyCoreSchemas(serviceInteractionDirectories, coreSchemaDirectory, targetDir){
	serviceInteractionDirectories.each { serviceInteractionDirectory ->
		def schemasFiles = getAllFilesMatching(coreSchemaDirectory, /.*\.xsd/)

		def serviceInteraction = serviceInteractionDirectory.name
		def serviceDirectory = serviceInteraction - 'Interaction'
		def coreSchemaTargetDir = "${targetDir}/${serviceDirectory}/src/main/resources/schemas/core_components"
		new File("${coreSchemaTargetDir}").mkdirs()

		schemasFiles.each {sourceSchemaFile ->
			def targetSchemaFile = new File("${coreSchemaTargetDir}/$sourceSchemaFile.name")
			FileUtils.copyFile(sourceSchemaFile, targetSchemaFile)}
	}

}

if( args.size() < 1){
	println "This tool generates service virtualising components based on service interactions found in sourceDir. They are generated in the dir where script is executed."
	println "Point sourceDir to the schemas dir containing:"
	println "core_components"
	println "interactions"
	println ""
	println "To be able to run this tool you need to have the virtualServiceArchetype installed, found under tools/virtualization/[trunk|tags]/virtualServiceArchetype/."
	println ""
	println "Required parameters: source directory [sourceDir] \n"
	println "PARAMETERS DESCRIPTION:"
	println "[sourceDir] is the base direcory where this script will start working to look for servivce interactions, e.g /repository/rivta/ServiceInteractions/riv/crm/scheduling/trunk "
	println ""
	println "OUTPUT:"
	println "New maven folders containing service interactions"
	return
}

def sourceDir = new File(args[0])
def targetDir = new File(".").getAbsolutePath()

new File("pom.xml").delete()
new File("${targetDir}/pom.xml") << new File("pomtemplate.xml").asWritable()

def serviceInteractionDirectories = getAllDirectoriesMatching(sourceDir,/.*Interaction$/)
def coreSchemaDirectory = getAllDirectoriesMatching(sourceDir,/core_components/)[0]

if (getAllDirectoriesMatching(coreSchemaDirectory, /.*/).size() > 0) {
	println ""
	println ""
	println "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
	println "Found directories in directory core_schemas, aborting..."
	println "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
	System.exit(0)
}

if (checkIfVersionNumbersInNameSpaces(serviceInteractionDirectories)) {
	println ""
	println ""
	println "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
	println "Found incorrect versions in namespaces, aborting..."
	println "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
	System.exit(0)
}

buildVirtualServices(serviceInteractionDirectories, targetDir)
copyServiceSchemas(serviceInteractionDirectories, targetDir)
copyCoreSchemas(serviceInteractionDirectories, coreSchemaDirectory, targetDir)

println ""
println ""
println "NOTE! Run mvn clean package to build deployable jar-files for service platform without adding to your local repo"
