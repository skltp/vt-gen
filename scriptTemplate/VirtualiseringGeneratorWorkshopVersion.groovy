#!/usr/bin/env groovy

package se.skltpservices.tools

import groovy.io.FileType

org.apache.commons.io.FileUtils

@Grab(group='commons-io', module='commons-io', version='1.3.2')
import org.apache.commons.io.FileUtils

@Grab(group='dom4j', module='dom4j', version='1.6.1')
import org.dom4j.io.SAXReader

/**
 * This script should help us to generate one virtual service.
 *
 * TO RUN:
 * Just execute ./VirtualiseringGeneratorWorkshopVersion.groovy and follow the instruction coming up.
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

def buildVirtualService(targetDir){

    def artifactId = 'ArtifactId'
    def groupId = 'a.valid.group.id'

    def wsdlFileName = 'name_of_wsdl.wsdl'
    def wsdlServiceName="${artifactId}EndpointService"
    def wsdlServiceNameSpace = 'a.wsdl.service.namespace'

    def httpEndpointAdress = "http://\${TP_HOST}:\${TP_PORT_HTTP}/CXF/services/service"
    def httpsEndpointAdress = "https://\${TP_HOST}:\${TP_PORT}/CXF/services/service"


		def mvnCommand = """mvn archetype:generate
		-DinteractiveMode=false
		-DarchetypeArtifactId=virtualServiceArchetype
		-DarchetypeGroupId=se.skltp.virtualservices.tools
		-DarchetypeVersion=1.5-SNAPSHOT
		-Duser.dir=${targetDir}
		-DgroupId=${groupId}
		-DartifactId=${artifactId}
		-Dversion=1.0
		-DvirtualiseringArtifactId=${artifactId}-virtualisering
    -DhttpsEndpointAdress=${httpsEndpointAdress}
    -DhttpEndpointAdress=${httpEndpointAdress}
		-DflowName=${artifactId}-Interaction-virtualisering-flow
		-DfeatureKeepaliveValue=\${feature.keepalive.${artifactId}:\${feature.keepalive}}
		-DfeatureResponseTimeoutValue=\${feature.keepalive.${artifactId}:\${SERVICE_TIMEOUT_MS}}
		-DserviceMethod=${artifactId}
		-DserviceWsdlFileDir=classpath:/schemas/interactions/${artifactId}Interaction/${wsdlFileName}
		-DserviceNamespace=${wsdlServiceNameSpace}
    -DwsdlServiceName=${wsdlServiceName}
		"""
		println "$mvnCommand"

		def process = mvnCommand.execute()
		process.waitFor()

		// Obtain status and output
		println "RETURN CODE: ${ process.exitValue()}"
		println "STDOUT: ${process.in.text}"
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

if( args.size() < 1){
  println "NOTE! This is a special implementation for training workshop."
	println "This tool generates service virtualising components based on service interactions found in sourceDir. They are generated in the dir where script is executed."
	println "Point sourceDir to the schemas dir containing:"
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

buildVirtualService(targetDir)
copyServiceSchemas(serviceInteractionDirectories, targetDir)

println ""
println ""
println "NOTE! Run mvn clean package to build deployable jar-files for service platform without adding to your local repo"
