@Grapes([
@Grab(group='org.apache.tika', module='tika-core', version='1.14'),
@Grab(group='org.apache.tika', module='tika-parsers', version='1.14'),
@Grab(group='commons-io', module='commons-io', version='2.5'),
@Grab(group='commons-lang', module='commons-lang', version='2.6'),
@Grab(group='org.hsqldb', module='hsqldb', version='2.3.4'),
@GrabConfig(systemClassLoader=true)
])

import groovy.io.*
import groovy.sql.Sql
import groovy.time.*
import groovy.json.*
import groovy.util.logging.Log
import static groovyx.gpars.GParsPool.withPool
import java.nio.file.*
import DBHelper
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
  String ftype 
  String hash
  String error = null
  String errorStack = null
  Path destFile
  Path srcFile
  int size
  boolean foundEXIF
  Date date = null
  String flags
  String gpsDescription
  double latitude = 0.0
  double longitude = 0.0

  String toString() {
    if(error != null) return "File: $file Type: $ftype Error: $error"
    if(date == null) return "File: $file Type: $ftype"
    if(gpsDescription == null) return "File: $file Type: $ftype Date: $date"
    return "File: $file Type: $ftype Date: $date Lat(E): $latitude Long(N): $longitude"
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
  Vector others = []
  def imageCount = new AtomicInteger()
  def sql = null

  String srcD = null
  String destImgDir = null
  String destDbDir = null
  Random rand = new Random()
  long today = (new Date()).getTime()
  def excludes = []
  int totalfiles = 0

  public ImageOrganizer() {
    String userhome = "E:\\" //System.getProperty("user.home")
    destImgDir = Paths.get(userhome,"ImageOrganizer","images").toString()
    destDbDir = Paths.get(userhome,"ImageOrganizer","db","db").toString()
    sql = Sql.newInstance('jdbc:hsqldb:file:'+destDbDir, 'SA', '', 'org.hsqldb.jdbc.JDBCDriver')
    initDataBase()
  }

  public initDataBase() {
    if(sql != null) {
      sql.execute("DROP TABLE IF EXISTS PicturesTemp")
      sql.execute("CREATE TABLE IF NOT EXISTS Pictures (id integer generated by default as identity, src varchar(800), filetype varchar(100), createdate timestamp, flags varchar(8), latitude double, longitude decimal, md5 varchar(500))")
      sql.execute("CREATE TABLE IF NOT EXISTS PicturesTemp (id integer generated by default as identity, src varchar(800), filetype varchar(100), createdate timestamp, flags varchar(8), latitude double, longitude decimal, md5 varchar(500))")
      sql.execute("CREATE TABLE IF NOT EXISTS History (id integer generated by default as identity, imgcount integer, notes varchar(800), importdate timestamp, status varchar(1))")
    }
  }
  // Private methods

  def loadTempToMemory(images) {
    sql.eachRow('SELECT * from PicturesTemp') { row ->
        File file = new File(row.src)
      
        ImageFile image = new ImageFile()
        image.file = file.getAbsolutePath()
        image.srcFile = Paths.get(row.src)
        
        if(row.createdate != null) image.date = (row.createdate as Date)
        else 
        image.ftype = row.filetype
        image.flags = row.flags
        image.latitude = row.latitude as double
        image.longitude = row.longitude as double
        image.hash = row.md5
        
        if(image.date == null) {
          image.destFile = Paths.get(destImgDir,"UNDATED",image.srcFile.getFileName().toString())
        }
        else {
          image.destFile = Paths.get(destImgDir,
                            image.date.format("yyyy"),image.date.format("MM"),image.date.format("d"),image.date.format("HH_mm_ss.SSS")
                            +"."+FilenameUtils.getExtension(row.src))
        }
        images.add(image)
    }
  }
  
  def getBoolInput(prompt) {
    def command = System.console().readLine "$prompt (Y/N)?"
    if(command != null && command.toLowerCase().startsWith('y')) return true 
    return false
  }

  def executeCommand(command) {
    if(sql != null) {
      try {
        sql.query(command) { resultSet ->  
          DBHelper.showResults(resultSet, DBHelper.pickColumns(sql, resultSet.getMetaData(), true), 100)
        }
      } catch(e) {
        System.out.println(e.getMessage())
      }
    }
  }

  def insertImage(table, image) {
    def timestamp = image.date == null? null : "'"+image.date.format("yyyy-MM-dd HH:mm:ss.SSS")+"'"
    if (table == "Pictures" || table == "PicturesTemp" ) sql.execute("insert into " + table + " (src, filetype, createdate, flags, latitude, longitude, md5) values ('${image.file}', '${image.ftype}', ${timestamp}, '${image.flags}', ${image.latitude}, ${image.longitude}, '${image.hash}')") 
  }

  // Public Methods
  
  def findDuplicates() {
    executeCommand("select MIN(Id) AS RowToKeep, md5 from PicturesTemp GROUP BY md5 HAVING COUNT(*) > 1")
  }
  
  def removeDuplicates() {
    sql.execute("DELETE FROM PicturesTemp Where ID in (select MIN(Id) from PicturesTemp GROUP BY md5 HAVING COUNT(*) > 1)")
  }
  
  def findInRepo() {
    executeCommand("SELECT P.SRC, T.SRC, P.CreateDate, T.CreateDate FROM Pictures AS P INNER JOIN PicturesTemp AS T ON P.md5 = T.md5")
  }
  
  def removeExisting() {
    sql.execute("DELETE FROM PicturesTemp WHERE ID IN (SELECT T.ID as ID from Pictures AS P INNER JOIN PicturesTemp AS T ON P.md5 = T.md5)")
  }

  def importToRepo() {
    Vector images = [] // has to be Vector so it's thread safe in the loop below
    loadTempToMemory(images)
    if(images.size() > 0 ) {
      def notes = System.console().readLine "Enter the notes for this import of ${images.size()} images.\nNotes: "
      def id = sql.executeInsert("INSERT INTO HISTORY (ImgCount, Notes, ImportDate, Status) VALUES (${images.size()}, $notes, NOW, 'P')")
      println("Starting import of ${images.size()} images.")
      withPool() {
        images.each {
          Files.createDirectories(it.destFile.getParent())
          Files.copy(it.srcFile, it.destFile, StandardCopyOption.REPLACE_EXISTING)
        }
        images.each {
          it.file = it.destFile.toString()
          insertImage("Pictures", it)
        }
      }
      sql.execute("DELETE FROM PicturesTemp")
      sql.execute("UPDATE HISTORY SET Status = 'D' WHERE ID = ${id[0][0]}")
    } else {
      println("There were no images to import.")
    }
  }

  def checkRepo() {
    def checkFiles = true
     
    print("Checking for interrupted imports ...")
    def count = executeCommand("SELECT Id, ImportDate from HISTORY WHERE Status = 'P'", true)
    if(count == 0) {
      checkFiles = getBoolImput("Nothing untoward found in History do you want to check cache")
    }
    if(checkFiles) {
      print("Checking for files that are not in the database ...")
      Path p = Paths.get(destImgDir)
      def filelist = []
      p.eachFileRecurse(FileType.FILES) {
        String fname = it.toString()
        filelist.add(fname) 
      }

      imageCount.set(0)
      others = []
      totalfiles = filelist.size()

      def found = []
      withPool() {
        found = filelist.collect { processFileTika(it, true) }
      }
      def updates = found.inject(0) { acc, val -> 
        if(val) acc + 1
      }
      print("Discoverd $updates new images not in the database.")
      if(updates > 0) sql.execute("UPDATE HISTORY SET Status = 'R' WHERE Status = 'P'") 
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

    imageCount.set(0)
    others = []
    totalfiles = filelist.size()
    
    withPool() {
      filelist.each { processFileTika(it, false) }
    }
  }

  def processFileTika(imageFile, checkCache) {
      def insert = false
      File file = new File(imageFile)
      
      ImageFile image = new ImageFile()
      image.file = file.getAbsolutePath()
      image.srcFile = Paths.get(imageFile)

      //Parser method parameters
      Parser parser = new AutoDetectParser()
      BodyContentHandler handler = new BodyContentHandler()
      Metadata metadata = new Metadata()
      FileInputStream inputstream = new FileInputStream(file)
      FileInputStream inputHashStream = new FileInputStream(file)
      ParseContext context = new ParseContext()
      
      try {
        parser.parse(inputstream, handler, metadata, context)
        String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(inputHashStream)
        Date date = metadata.getDate(TikaCoreProperties.CREATED)
        String ftype = metadata.get(Metadata.CONTENT_TYPE)
        String lat = metadata.get(TikaCoreProperties.LATITUDE)
        String longt = metadata.get(TikaCoreProperties.LONGITUDE)

        if(date != null) {
          long milli = (date.getTime() % 1000)
          if(milli == 0) date = new Date(date.getTime() + rand.nextInt(1000))
          image.date = date
        } else { //base it on modified time
          if ((today - file.lastModified()) > 94608000000L ) { // 3 years approx. 1000*3600*24*365*3
            image.date = new Date(file.lastModified())
            image.flags = 'M'
          }
        }
        if(ftype != null) image.ftype = ftype
        if(lat != null) image.latitude = lat as double
        if(longt != null) image.longitude = longt as double
        if(lat != null && longt != null) image.gpsDescription = "Latitude: ${lat} Longitude: ${longt}"
        image.hash = md5
        
        if(ftype.indexOf("octet-stream") == -1) image.foundEXIF = true

      } catch(all) {
        image.foundEXIF = false
        image.error = all.getMessage()
        image.errorStack = ExceptionUtils.getStackTrace(all)
      }
      finally {
        if(inputHashStream != null) inputHashStream.close()
      }
      if(image.foundEXIF) {
        imageCount.getAndIncrement()
        insert = true
        def table =  "PicturesTemp"
        if(checkCache) {
          def rows = sql.rows("SELECT Id from Pictures WHERE ID = '${image.hash}'")
          if( rows.size() > 0 ) insert = false
          table = "Pictures"
        }
        if(insert) insertImage(table, image)
      }
      else others.add(image)
      return insert 
  }
}

def cli = new CliBuilder(usage:'Pictures.groovy -s SourceDirectory')
cli.with {
  h longOpt: 'help', 'Show usage information.'
  s longOpt: 'source', args: 1, argName: 'src', 'The source directory in which to look for image files.'
  e longOpt: 'exclude', args: 1, argName: 'exclude', 'Comma separated list of extensions to exclude in the source directory.'
  a longOpt: 'auto', args: 1, argName: 'auto', 'Automatically remove duplicates and add files that are not in the repository.'
}

def options = cli.parse(args)
  
if(!options || options.h || args.length == 0) {
  cli.usage()
  return
}

def extraArgs = options.arguments()

String src = "."

//if(options.a) auto = options.d
if(options.s) src = options.s

def io = new ImageOrganizer(srcD: src)
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
    println "select ...         Any query against the picture database [Table:Pictures, History, PicturesTemp]."
    println "others             Print the names of the files that were not recognized as images."
    println "dedup              Find duplicates based on hash and report them."
    println "import             Import the files to the destination folder."
    println "check              Check the cache."
  } else if (cmd[0] == "exit") {
    keepProcessing = false
  } else if (cmd[0] == "status") {
    Date now = new Date()
    double percentDone = ((((io.imageCount.get() + io.others.size())/(double)io.totalfiles)*10000.0) as int)/100 //
    println "Time Elapsed: ${TimeCategory.minus(now,timeStarted)} Total Files: ${io.totalfiles} Files Processed (%): ${percentDone}"
    println "Found ${io.imageCount.get()} images and ${io.others.size()} other unrecognized files."
  } else if (cmd[0] == "others" || cmd[0] == "other") {
    if( cmd.size() > 1 && cmd[1].isInteger()) {
      int index = cmd[1] as int
      if(io.others.size() > index) {
        ImageFile im = list[index]
        println im.printDetails()
      } else {
        println "There are only ${io.others.size()} files in the others list. The index specified [${index}] was higher."
      }
    } else {
      io.others.eachWithIndex { elem, indx -> println "${indx}] $elem" }
    }
  } else if (cmd[0] == "dedup") {
    io.findDuplicates()
    if(io.getBoolInput("Do you want to remove duplicates shown")) {
      io.removeDuplicates()
      println "Duplicate images have been removed from the temp database."
    }
    println "Finding images that already exist in the master repository."
    io.findInRepo()
    if(io.getBoolInput("Do you want to remove existing images")) {
      io.removeExisting()
      println "Existing images have been removed from the temp database."
    }
  } else if(cmd[0] =="select") {
    io.executeCommand(command)
  } else if(cmd[0] =="import") {
    io.removeDuplicates()
    io.removeExisting()
    io.importToRepo()
  } else if(cmd[0] =="check") {
    io.checkRepo()
  } else {
    println "Invalid command. Please type help to see your options."
  }
}
