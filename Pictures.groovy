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
    return lines.join("\n")
  }
}

@Log
class ImageOrganizer {
  Vector others = []
  Vector intDuplicates = []
  Vector extDuplicates = []
  def imageCount = new AtomicInteger()
  def sql = null

  String srcD = null
  String destImgDir = null
  File logFile = null
  String destDbDir = null
  Random rand = new Random()
  long today = (new Date()).getTime()
  def excludes = []
  int totalfiles = 0

  public ImageOrganizer() {
    String userhome = "E:\\" //System.getProperty("user.home")
    Date now = new Date()
    destImgDir = Paths.get(userhome,"ImageOrganizer","images").toString()
    Path logFilePath = Paths.get(userhome,"ImageOrganizer","logs",now.format("yyyy-MM-d")+".log")
    logFile = new File(logFilePath.toString())
    Files.createDirectories(logFilePath.getParent())
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
        image.ftype = row.filetype
        image.flags = row.flags
        image.latitude = row.latitude as double
        image.longitude = row.longitude as double
        image.hash = row.md5
        
        if(image.flags != null && image.flags.indexOf("M") != -1) {
          image.destFile = Paths.get(destImgDir,"UNDATED",image.date.format("yyyy"),image.date.format("MM"),image.date.format("d"),image.srcFile.getFileName().toString())
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

  def executeCommand(command, show= true) {
    if(sql != null) {
      try {
        sql.query(command) { resultSet ->  
          DBHelper.showResults(resultSet, DBHelper.pickColumns(sql, resultSet.getMetaData(), show), 100)
        }
      } catch(e) {
        System.out.println(e.getMessage())
      }
    }
  }

  def insertImage(table, image) {
    def timestamp = image.date == null? null : "'"+image.date.format("yyyy-MM-dd HH:mm:ss.SSS")+"'"
    def src = image.file.startsWith(destImgDir)?image.file.drop(destImgDir.size()+1): image.file
    def flags = image.flags == null? null : "'"+image.flags+"'"
    def ftype = image.ftype == null? null : "'"+image.ftype+"'"
    if (table == "Pictures" || table == "PicturesTemp" ) sql.execute("insert into " + table + " (src, filetype, createdate, flags, latitude, longitude, md5) values ('${src}', ${ftype}, ${timestamp}, ${flags}, ${image.latitude}, ${image.longitude}, '${image.hash}')") 
  }
  
  def removeImagesWithId(images) {
    if(images.size() > 0) {
      logFile.append("Removing ${images.size()} images.\nID: $images\n")
      int[] delCount = null
      sql.withTransaction {
        delCount = sql.withBatch("Delete from PicturesTemp WHERE ID = ?") { ps ->
          images.each { ps.addBatch(it) }
        }
      }
      def deleted = delCount.inject(0) { acc, val -> acc += val } 
      logFile.append("Successfully removed ${deleted} images.\n")
      print("Successfully removed ${deleted} images.\n")
    }
  }

  // Public Methods
  
  def findDuplicates() {
    intDuplicates = new Vector()
    def lastHash = ""
    def lines = []
    int count = 0
    logFile.append("Looking for duplicate images in the directory: $srcD.\n")
    sql.eachRow("Select id, src, md5 from PicturesTemp WHERE md5 IN (SELECT md5 from PicturesTemp GROUP BY md5 HAVING COUNT(*) > 1) order by md5") { row ->
      if(lastHash != row.md5) {
        lines.add("Source: $row.src Hash: $row.md5 Id: $row.id")
        lastHash = row.md5
      }
      else {
        count++
        lines.add("---> Duplicate: $row.src Hash: $row.md5 Id: $row.id")
        intDuplicates.add(row.id as int)
      }
    }
    logFile.append("Found $count duplicate images in the directory: $srcD.\n");
    print("Found $count duplicate images in the directory: $srcD.\n");
    logFile.append(lines.join("\n")+"\n")
  }
  
  def removeDuplicates() {
    removeImagesWithId(intDuplicates)
  }

  
  def findInRepo() {
    extDuplicates = new Vector()
    def lines = []
    logFile.append("Looking for images that already exist in repository.\n")
    sql.eachRow("SELECT T.ID as ID, T.SRC as SRC, P.SRC as RepoSrc FROM Pictures AS P INNER JOIN PicturesTemp AS T ON P.md5 = T.md5") { row ->
      extDuplicates.add(row.id as int)
      lines.add("Image: ${row.src} Repository: ${row.RepoSrc}")
    }
    logFile.append("Found ${lines.size()} images that already exist in the repository.\n");
    print("Found ${lines.size()} images that already exist in the repository.\n");
    logFile.append(lines.join("\n")+"\n")
  }
  
  def removeExisting() {
    removeImagesWithId(extDuplicates)
  }

  def importToRepo() {
    Vector images = [] // has to be Vector so it's thread safe in the loop below
    loadTempToMemory(images)
    if(images.size() > 0 ) {
      def notes = "Importing ${images.size()} images from $srcD"
      logFile.append(notes+"\n")
      notes = "'"+notes+"'"
      def id = sql.executeInsert("INSERT INTO HISTORY (ImgCount, Notes, ImportDate, Status) VALUES (${images.size()}, $notes, NOW, 'P')")
      def imageImpFile = new AtomicInteger()
      def imageImpDb = new AtomicInteger()
      withPool() {
        images.each {
          Files.createDirectories(it.destFile.getParent())
          Files.copy(it.srcFile, it.destFile, StandardCopyOption.REPLACE_EXISTING)
          imageImpFile.getAndIncrement()
        }
        images.each {
          it.file = it.destFile.toString()
          insertImage("Pictures", it)
          imageImpDb.getAndIncrement()
        }
      }
      if( imageImpFile.get() == imageImpDb.get() && imageImpFile.get() == images.size() ) { 
        sql.execute("DELETE FROM PicturesTemp")
        sql.execute("UPDATE HISTORY SET Status = 'D' WHERE ID = ${id[0][0]}")
        logFile.append("Successfully imported ${images.size()} files and also updated the image database.\n")
        print("Successfully imported ${images.size()} files and also updated the image database.\n")
      }
    } else {
      logFile.append("There were no images to import.\n")
      print("There were no images to import.\n")
    }
  }

  def checkRepo() {
    def checkFiles = true
     
    println("Checking for interrupted imports ...")
    def count = executeCommand("SELECT Id, ImportDate, Status from HISTORY WHERE Status = 'P'", true)
    if(count == 0) {
      checkFiles = getBoolImput("Nothing untoward found in History do you want to check cache?")
    }
    if(checkFiles) {
      println("Checking for files that are not in the database ...")
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
        if(val) acc += 1
        return acc
      }
      print("Discoverd $updates new images not in the database.")
      sql.execute("UPDATE HISTORY SET Status = 'R' WHERE Status = 'P'") 
    }
  }

  def processFiles() {
    logFile.append("Importing files from ${srcD} into temporary database for analysis.\n")
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
    logFile.append("Found ${imageCount.get()} images and ${others.size()} other unrecognized files from $totalfiles files in directory $srcD.\n")
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
          image.date = new Date(file.lastModified())
          image.flags = 'M'
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
          def rows = sql.rows("SELECT Id from Pictures WHERE md5 = ${image.hash}")
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

boolean keepProcessing = true  
if(!options || options.h || args.length == 0) {
  cli.usage()
  keepProcessing = false
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
    io.removeDuplicates()
    println "Finding images that already exist in the master repository."
    io.findInRepo()
    io.removeExisting()
  } else if(cmd[0] =="select") {
    io.executeCommand(command)
  } else if(cmd[0] =="import") {
    io.findDuplicates()
    io.removeDuplicates()
    io.findInRepo()
    io.removeExisting()
    Thread.startDaemon() {
      io.importToRepo()
    }
  } else if(cmd[0] =="check") {
    io.checkRepo()
  } else {
    println "Invalid command. Please type help to see your options."
  }
}
