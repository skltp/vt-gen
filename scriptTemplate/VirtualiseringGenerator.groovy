#!/usr/bin/env groovy
package se.skltpservices.tools

@Grab(group='commons-io', module='commons-io', version='1.3.2')
@Grab(group='dom4j', module='dom4j', version='1.6.1')

import groovy.io.FileType
// org.apache.commons.io.FileUtils
import org.apache.commons.io.FileUtils
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


def printelnerr = System.err.&println

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
		if(namespace.text.contains('Responder')){
			featureKeepAliveServiceContractNameSpace = namespace.text.replaceAll(':', '.')
		}
	}
	return featureKeepAliveServiceContractNameSpace
}

def checkDirectoriesAndFiles(serviceInteractionDirectories) {
	def found = false
	def multiple = false
	serviceInteractionDirectories.each { serviceInteractionDirectory ->
		if (getAllFilesMatching(serviceInteractionDirectory, /.*\.wsdl/).size() != 1) {
			multiple = true
			println "ERROR: Found multiple WSDL files in " + serviceInteractionDirectory.toString()
			println "\tPlease only supply one WSDL file for each contract!"
		}

		def xsdFiles = getAllFilesMatching(serviceInteractionDirectory, /.*\.xsd/).findAll { file ->
			!(file.name ==~ /.*_ext.*/)
		}

		if (xsdFiles.size() != 1) {
			multiple = true
			println "ERROR: Found more than one XSD file in " + serviceInteractionDirectory.toString()
			xsdFiles.each {file -> println("\t" + file.name)}
		}

		// Check that namespace has a 1-digit version number, if not fail!
		def version = getServiceContractNameSpaceVersion(xsdFiles[0])
		if (version.indexOf('.') > 0) {
			found = true
			println "Incorrect version number for the following service contract! " + serviceInteractionDirectory.toString() + ". Version is: " + version
		}
	}

	if (multiple) {
		printelnerr ""
		printelnerr ""
		printelnerr "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
		printelnerr "Found multiple XSD and/or WSDL files, aborting..."
		printelnerr "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
		System.exit(0)
	} else if (found) {
		printelnerr ""
		printelnerr ""
		printelnerr "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
		printelnerr "Found incorrect versions in namespaces, aborting..."
		printelnerr "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
		System.exit(0)
	}
}

def getServiceContractNameSpaceVersion(xsdFile){
	def namespace = new SAXReader().read(xsdFile).getRootElement().attribute("targetNamespace");
	def featureServiceContractNameSpaceVersion = namespace.text.substring(namespace.text.lastIndexOf(':')+1, namespace.text.length());

	return featureServiceContractNameSpaceVersion
}


def getServiceContractVersion(xsdFile){
	def serviceContractVersion = 'No service contract version found'

	//Read version from root element
	serviceContractVersion = new SAXReader().read(xsdFile).getRootElement().attributeValue("version")
	return serviceContractVersion
}

def buildVirtualServices(serviceInteractionDirectories, targetDir, servicedomainVersion, shortname){

	serviceInteractionDirectories.each { serviceInteractionDirectory ->

		def (name, schemaDir) = serviceInteractionDirectory.parent.split("schemas")
		schemaDir = schemaDir.replace('\\','/') // If generated on windows till will have the '\' separator that will break mule.

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

		//Version of generator for virtualization
    	def virtualizationGeneratorVersion = 'virtGen-2.1'

		def serviceContractNameSpace = getServiceContractNameSpace(xsdFiles[0])
		def serviceContractVersion = getServiceContractVersion(xsdFiles[0])
		
		def endpoinAddress1 = ""
		def endpointAddress2 = ""
		if(shortname.size() == 0) {
		   // För tjänster som skall ha subdomän i ändpunktens adressen (default)
		   endpointAddress1 = "-DhttpsEndpointAdress=https://\${TP_HOST}:\${TP_PORT}/\${TP_BASE_URI}/$maindomain/$subdomainAdress/$serviceRelativePath"
		   endpointAddress2 = "-DhttpEndpointAdress=http://\${TP_HOST}:\${TP_PORT_HTTP}/\${TP_BASE_URI}/$maindomain/$subdomainAdress/$serviceRelativePath"
		} else {
		   // För tjänster som INTE skall ha subdomän i ändpunktens adressen (default)
		  endpointAddress1 = "-DhttpsEndpointAdress=https://\${TP_HOST}:\${TP_PORT}/\${TP_BASE_URI}/$serviceRelativePath"
		  endpointAddress2 = "-DhttpEndpointAdress=http://\${TP_HOST}:\${TP_PORT_HTTP}/\${TP_BASE_URI}/$serviceRelativePath"
		}
		
		def mvnCommand = """archetype:generate
		-DinteractiveMode=false
		-DarchetypeArtifactId=virtualServiceArchetype
		-DarchetypeGroupId=se.skltp.virtualservices.tools
		-DarchetypeVersion=2.1
		-Duser.dir=${targetDir}
		-DgroupId=se.skltp.virtualservices.${maindomain}.${subdomainGroupId}
		-DartifactId=${artifactId}
		-Dversion=${virtualizationGeneratorVersion}
		-DvirtualiseringArtifactId=${maindomain}-${subdomainFlow}-${servicedomainVersion}-${artifactId}-${serviceContractVersion}
		 $endpointAddress1
		 $endpointAddress2
		-DflowName=${maindomain}-${subdomainFlow}-${artifactId}-${serviceContractVersion}-Interaction-virtualisering-flow
		-DfeatureKeepaliveValue=\${feature.keepalive.${serviceContractNameSpace}:\${feature.keepalive}}
		-DfeatureResponseTimeoutValue=\${feature.featureresponsetimeout.${serviceContractNameSpace}:\${SERVICE_TIMEOUT_MS}}
		-DserviceMethod=${artifactId}
		-DserviceWsdlFileDir=classpath:/schemas$schemaDir/${artifactId}Interaction/${wsdlFileName}
		-DserviceNamespace=${serviceInteractionNameSpace}
    	-DwsdlServiceName=${artifactId}ResponderService
		"""

		if (System.properties['os.name'].toLowerCase().contains("windows")) {
			mvnCommand = "mvn.cmd " + mvnCommand + " 1>> mvn.out 2>>&1"
			mvnCommand = mvnCommand.replaceAll("\n","^\n")
		} else {
			mvnCommand = "mvn " +  mvnCommand
		}

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
		FileUtils.copyDirectory(coreSchemaDirectory, new File(coreSchemaTargetDir))
	}
}

// --

def appName = this.getClass().getSimpleName()
def cli = new CliBuilder(
	header: "---",
	usage: "${appName} --sourceDir <path_to_dir> --servicedomain <servicedomain_version> ",
	footer: "---\nThis tool generates service virtualising components based on service interactions found in <sourceDir>." 
			+ "They are generated in the directory where script is executed."
			+ "\nPoint --sourceDir to the schemas dir containing: [core_components] [interactions]."
			+ "To be able to run this tool you need to have the virtualServiceArchetype installed,"
			+ "found under [tools/virtualization/[trunk|tags]/virtualServiceArchetype/]"
			+ "\nOUTPUT: New maven folders containing service interactions"
			+ "\n"
)

cli.with {
    d( longOpt: 'sourceDir', 'path to directory with jars', args: 1, required: true )
    s( longOpt: 'servicedomain', 'version for the service domain, e.g 2.1.0-RC2', args: 1, required: true )
    n( longOpt: 'shortname', '(optional). If it has a value no subdomain is added to the endpoint URL', args: 1, required: false )
    h( longOpt: 'help', 'Print help', required: false )
}

OptionAccessor options = cli.parse(args)

if (!options) return
if (options.h) cli.usage()
def sourceDir = new File(options.d)
def servicedomainVersion = options.s
def shortname = options.n ? options.n : ""
def scriptDir = new File(getClass().protectionDomain.codeSource.location.path).parent
def targetDir = new File('.').getCanonicalPath()

println "sourceDir :${sourceDir}"
println "servicedomainVersion :${servicedomainVersion}"
println "shortname: ${shortname}"
println "scriptDir: ${scriptDir}"
println "targetDir: ${targetDir}"

def serviceInteractionDirectories = getAllDirectoriesMatching(sourceDir,/.*Interaction$/)
def coreSchemaDirectory = getAllDirectoriesMatching(sourceDir,/core_components/)[0]

checkDirectoriesAndFiles(serviceInteractionDirectories)

new File("pom.xml").delete()
//new File("${targetDir}/pom.xml") << new File(scriptDir, "pomtemplate.xml").asWritable()
new File(targetDir, "pom.xml") << new File(scriptDir, "pomtemplate.xml").asWritable()

buildVirtualServices(serviceInteractionDirectories, targetDir, servicedomainVersion, shortname)
copyServiceSchemas(serviceInteractionDirectories, targetDir)
copyCoreSchemas(serviceInteractionDirectories, coreSchemaDirectory, targetDir)

println ""
println "NOTE! Run mvn clean package to build deployable jar-files for service platform without adding to your local repo"
