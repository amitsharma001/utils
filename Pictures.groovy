@GrabResolver(name='snapshot', root='https://repository.apache.org/content/repositories/snapshots/')
@Grab(group='org.apache.commons', module='commons-imaging', version='1.0-SNAPSHOT')
@Grab(group='commons-io', module='commons-io', version='2.5')


import groovy.io.*
import groovy.time.*
import java.nio.file.*
import groovy.json.*
import org.apache.commons.io.FilenameUtils
import groovy.util.logging.Log
import static groovyx.gpars.GParsPool.withPool

import org.apache.commons.imaging.*
import org.apache.commons.imaging.common.*
import org.apache.commons.imaging.formats.jpeg.*
import org.apache.commons.imaging.formats.tiff.*
import org.apache.commons.imaging.formats.tiff.constants.*
import java.util.concurrent.atomic.*
import java.util.concurrent.*

class ImageFile {
  String file
  String dateHash
  String error = null
  Path destFile
  Path srcFile
  int size
  boolean foundEXIF
  Date date = null
  String gpsDescription
  double latitude
  double longitude

  String toString() {
    if(error != null) return "File: $file Error: $error"
    if(date == null) return "File: $file"
    if(gpsDescription == null) return "File: $file Date: $date"
    return "File: $file Date: $date Lat(E): $latitude Long(N): $longitude"
  }

  String printDetails() {
    def lines = []
    lines << "File: $file"
    lines << "Date: $date"
    lines << "GPS Location: $gpsDescription"
    lines << "Latitude North: $latitude Longitude East: $longitude" 
    lines << "Destination: ${destFile.toString()}" 
    return lines.join("\r\n")
  }
}

@Log
class ImageOrganizer {
  Vector images = []
  Vector others = []
  ConcurrentHashMap map = new ConcurrentHashMap()

  String srcD = null
  String destD = null
  def excludes = []
  int totalfiles = 0

  def getTagValue(def metadata, def tagInfo) {
    def field = metadata.findEXIFValueWithExactMatch(tagInfo)
    if( field != null ) return field.getValueDescription()
    return null
  }
  
  def findDuplicates() {
    withPool() {
      images.each {
        Vector v = new Vector()
        v.add(it)
        Vector clash = map.putIfAbsent(it.dateHash, v)
        clash.add(it)
      }
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
      filelist.each { processFile(it) }
    }
  }

  def processFile(imageFile) {
    File f = new File(imageFile)

    ImageFile image = new ImageFile()
    image.file = f.getAbsolutePath()
  
    try {
      ImageMetadata metadata = Imaging.getMetadata(f)
      if( metadata != null ) {
        String date = getTagValue(metadata, ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL)[1..-2]
        if(date.length() > 15) image.date = Date.parse("yyyy:MM:dd hh:mm:ss",date)
        def exifMetadata = metadata.getExif()
        if (null != exifMetadata) {
          def gpsInfo = exifMetadata.getGPS()
          if(gpsInfo != null) {
            image.gpsDescription = gpsInfo.toString()
            image.longitude = gpsInfo.getLongitudeAsDegreesEast()
            image.latitude = gpsInfo.getLatitudeAsDegreesNorth()
          }
        }
      }
      image.srcFile = Paths.get(imageFile)
      if(image.date == null) {
        image.destFile = Paths.get(destD,"UNDATED",image.srcFile.getFileName().toString())
        image.dateHash = "UNDATED" + image.srcFile.getFileName().toString()
      }
      else {
        image.destFile = Paths.get(destD,image.date.format("yyyy"),image.date.format("MMM"),image.date.format("HH_mm_ss")+"."+FilenameUtils.getExtension(imageFile))
        image.dateHash = image.date.format("yyyy") + "_"+ image.date.format("MMM") + "_"+ image.date.format("HH_mm_ss")
      }
      image.foundEXIF = true
    } catch(all) {
      image.foundEXIF = false
      image.error = all.getMessage()
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
def prompt = "io >"
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
  } else if (cmd[0] == "image" && cmd.size() > 1 && cmd[1].isInteger()) {
    int index = cmd[1] as int
    if(io.images.size() > index) {
      ImageFile im = io.images[index]
      println im.printDetails()
    } else {
      println "There are only ${io.images.size()} images in the processed pool. The index specified [${index}] was higher."
    }
  } else {
    println "Invalid command. Please type help to see your options."
  }

}
