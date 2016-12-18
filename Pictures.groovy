@Grab(group='org.apache.tika', module='tika-core', version='1.14')
@Grab(group='org.apache.tika', module='tika-parsers', version='1.14')
@Grab(group='commons-io', module='commons-io', version='2.5')
@Grab(group='commons-lang', module='commons-lang', version='2.6')


import groovy.io.*
import groovy.time.*
import groovy.json.*
import groovy.util.logging.Log
import static groovyx.gpars.GParsPool.withPool
import java.nio.file.*
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.exception.ExceptionUtils

import org.apache.tika.parser.AutoDetectParser
import org.apache.tika.parser.ParseContext
import org.apache.tika.parser.Parser
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.sax.BodyContentHandler

import java.util.concurrent.atomic.*
import java.util.concurrent.*

class ImageFile {
  String file
  String dateHash
  String error = null
  String errorStack = null
  Path destFile
  Path srcFile
  int size
  boolean foundEXIF
  Date date = null
  String gpsDescription
  double latitude = 0.0
  double longitude = 0.0

  String toString() {
    if(error != null) return "File: $file Error: $error"
    if(date == null) return "File: $file"
    if(gpsDescription == null) return "File: $file Date: $date"
    return "File: $file Date: $date Lat(E): $latitude Long(N): $longitude"
  }

  String printDetails() {
    def lines = []
    lines << "File: $file"
    if(date != null) lines << "Date: $date"
    if(gpsDescription != null) lines << "GPS Location: $gpsDescription"
    if(latitude > 0) lines << "Latitude North: $latitude Longitude East: $longitude" 
    lines << "Destination: ${destFile.toString()}" 
    if(error != null) lines << "Error: $error" 
    if(errorStack != null) lines << "StackTrace: $errorStack" 
    return lines.join("\r\n")
  }
}

@Log
class ImageOrganizer {
  Vector images = []
  Vector others = []
  def dupMap = []

  String srcD = null
  String destD = null
  def excludes = []
  int totalfiles = 0

  def findDuplicates() {
    dupMap = [:]
    images.each {
      def list = []
      if(dupMap[it.dateHash] != null) {
        list = dupMap[it.dateHash]
      } 
      list << it
      dupMap.put(it.dateHash, list)
    }
  }

  def writeToDestination(test=False) {
    withPool() {
      images.each {
        if(!test) {
          Files.createDirectories(image.destFile.getParent())
          Files.copy(srcFile, image.destFile, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }
  }

  def processFiles() {
    Path p = Paths.get(srcD)
    def filelist = []
    p.eachFileRecurse(FileType.FILES) {
      String fname = it.toString()
      boolean exclude = false
      excludes.each {
        if(fname.endsWith(it)) exclude = true
      }
      if(!exclude) filelist.add(fname) 
    }
    totalfiles = filelist.size()
    withPool() {
      filelist.each { processFileTika(it) }
    }
  }

  def processFileTika(imageFile) {
      File file = new File(imageFile);
      
      ImageFile image = new ImageFile()
      image.file = file.getAbsolutePath()
      image.srcFile = Paths.get(imageFile)

      //Parser method parameters
      Parser parser = new AutoDetectParser();
      BodyContentHandler handler = new BodyContentHandler();
      Metadata metadata = new Metadata();
      FileInputStream inputstream = new FileInputStream(file);
      ParseContext context = new ParseContext();
      
      try {
        parser.parse(inputstream, handler, metadata, context);
        //System.out.println(handler.toString());

        Date date = metadata.getDate(TikaCoreProperties.CREATED)
        String lat = metadata.get(TikaCoreProperties.LATITUDE)
        String longt = metadata.get(TikaCoreProperties.LONGITUDE)

        if(date != null) image.date = date
        if(lat != null) image.latitude = lat as double
        if(longt != null) image.longitude = longt as double
        if(lat != null && longt != null) image.gpsDescription = "Latitude: ${lat} Longitude: ${longt}"
        
        if(image.date == null) {
          image.destFile = Paths.get(destD,"UNDATED",image.srcFile.getFileName().toString())
          image.dateHash = "UNDATED" + image.srcFile.getFileName().toString()
        }
        else {
          image.destFile = Paths.get(destD,
                            image.date.format("yyyy"),image.date.format("MMM"),image.date.format("d"),image.date.format("HH_mm_ss")
                            +"."+FilenameUtils.getExtension(imageFile))
          image.dateHash = image.date.format("yyyy") + "_"+ image.date.format("MMM") + 
                            "_"+ image.date.format("d") + image.date.format("HH_mm_ss")
        }
        image.foundEXIF = true

      } catch(all) {
        image.foundEXIF = false
        image.error = all.getMessage()
        image.errorStack = ExceptionUtils.getStackTrace(all)
      }
      if(image.foundEXIF) images.add(image)
      else others.add(image)
  }
}

def cli = new CliBuilder(usage:'Pictures.groovy -s SourceDirectory -d DestinationDirectory')

cli.with {
  h longOpt: 'help', 'Show usage information.'
  s longOpt: 'source', args: 1, argName: 'src', 'The source directory in which to look for image files.'
  e longOpt: 'exclude', args: 1, argName: 'exclude', 'The extensions to exclude in the source directory.'
  d longOpt: 'destination', args: 1, argName: 'destination', 'The destination directory where the files are organized based on the date the picture was taken.'
}

def options = cli.parse(args)
  
if(!options || options.h || args.length == 0) {
  cli.usage()
  return
}

def extraArgs = options.arguments()

String dest = "Photos"
String src = "."

if(options.d) dest = options.d
if(options.s) src = options.s

def io = new ImageOrganizer(srcD: src, destD: dest)
if(options.e) io.excludes = options.e.tokenize(",")

Date timeStarted = new Date()

Thread.startDaemon {
  io.processFiles()
}


boolean keepProcessing = true
def prompt = "io> "
def command = null

while(keepProcessing) {
  command = System.console().readLine prompt
  if(command == null) continue
  cmd = command.toLowerCase().tokenize(' ')
  if(cmd[0] == "help") {
    println "The following commands are supported:"
    println "exit               Exit the program."
    println "status             Print status."
    println "others             Print the names of the files that were not recognized as images."
    println "dedup              Find duplicates based on time and report them."
    println "images             Print the names of image files that have been read."
    println "image index        Print the details of the image at index."
    println "copyTest           Prints what the copy operation would do."
    println "copy               Copies the files to the destination folder."
  } else if (cmd[0] == "exit") {
    keepProcessing = false
  } else if (cmd[0] == "status") {
    Date now = new Date()
    double percentDone = ((((io.images.size() + io.others.size())/(double)io.totalfiles)*10000.0) as int)/100 //
    println "Time Elapsed: ${TimeCategory.minus(now,timeStarted)} Total Files: ${io.totalfiles} Files Processed (%): ${percentDone}"
    println "Found ${io.images.size()} images and ${io.others.size()} other unrecognized files."
  } else if (cmd[0] == "others") {
    io.others.eachWithIndex { elem, indx -> println "${indx}] $elem" }
  } else if (cmd[0] == "images") {
    io.images.eachWithIndex { elem, indx -> println "${indx}] $elem" }
  } else if (cmd[0] == "dedup") {
    io.findDuplicates()
    io.dupMap.each { name, value ->
      if (value.size()> 1) {
        println "Duplicate entries for the name $name"
        value.each { println "-- ${it}" }
      }
    }
  } else if ((cmd[0] == "image" || cmd[0] == "other") && cmd.size() > 1 && cmd[1].isInteger()) {
    int index = cmd[1] as int
    def list = (cmd[0] == "image")? io.images : io.others
    if(list.size() > index) {
      ImageFile im = list[index]
      println im.printDetails()
    } else {
      println "There are only ${list.size()} files in the {cmd[0]} list. The index specified [${index}] was higher."
    }
  } else {
    println "Invalid command. Please type help to see your options."
  }

}
