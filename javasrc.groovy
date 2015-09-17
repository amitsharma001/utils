import java.nio.file.*
import groovy.util.*;
import groovy.util.logging.*
import groovy.io.*

class SourceFile {
  String svnPath;
  String packageName; 
}

@Log
class JOptProcessor {
  String basedir, destdir
  def files = [], warnings = [], classpath = [], optfiles = []

  def processFiles() {
    optfiles.each {
      processJavaOpts(it)
    }
  }

  def processJavaOpts(String javaOptsFile) {
    File file = new File(javaOptsFile)
    log.info "Processing file ${file.getAbsolutePath()}" 
    def lines = file.readLines()
    lines.each { line ->
      try {
        if(line.startsWith("-classpath")) {
          String cp = line.substring(line.lastIndexOf(' ')+1)
          classpath += cp.tokenize(";")
        } else if(line.replaceAll(' ','').size() > 0) {
          String folder = line.substring(0,line.lastIndexOf('\\'))
          String filep = line.substring(line.lastIndexOf('\\')+1).replace("*.",".*\\.")
          log.fine "Processing folder ${folder} for file pattern ${filep}." 
          Path p = Paths.get(basedir, folder)
          p.eachFileMatch(FileType.FILES, ~filep) { javaFile ->
            log.fine "FILE:: ${javaFile.normalize().toAbsolutePath()}"
            javaFile.withReader('UTF-8') { reader -> 
              boolean foundPackage = false
              while((line = reader.readLine()) != null) {
                def packMatch = (line =~ /.*package ?(.*);.*/)
                if(packMatch.matches()) {
                  SourceFile sf = new SourceFile(svnPath: javaFile.normalize().toAbsolutePath(), packageName: packMatch[0][1])
                  files << sf
                  break;
                }
              }
            }
          }  
        }
      } catch (all) {
        def war = "Could not process line ${javaOptsFile}:${line} due to ${all}"
        log.warning(war)
        warnings << war
      }
    }
  }

  def printInfo() {
    println "ClassPaths: ${classpath.size}"
    println "Source Files: ${files.size}"
    println "Warnings: ${warnings.size}"
    println classpath
  }

  def createJavaPackage() {
    def newp = [:]
    files.each {
      Path srcFile = Paths.get(it.svnPath)
      Path destFile = Paths.get(destdir,'src',it.packageName.replace(".","\\"),srcFile.getFileName().toString())
      if(!newp[it.packageName]) {
        newp[it.packageName] = it.packageName
        log.info "Creating package ${it.packageName}" 
        Files.createDirectories(destFile.getParent())
      }
      Files.copy(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING)
    }
    Files.createDirectories(Paths.get(destdir,'lib'))
    classpath.each {
      Path srcFile = Paths.get(it)
      Path destFile = Paths.get(destdir,'lib',srcFile.getFileName().toString())
      Files.copy(srcFile, destFile, StandardCopyOption.REPLACE_EXISTING)
    }
  }
}

def process(args) {
  def cli = new CliBuilder(usage:'javasrc.groovy -[bd] java_opt_file [... java_opt_file]')
  cli.with {
    h longOpt: 'help', 'Show usage information.'
    b longOpt: 'basedir', args: 1, argName: 'basedir', 'The base dir from which to resolve all relative paths. Defaults to current dir if not set.'
    d longOpt: 'destdir', args: 1, argName: 'destdir', 'The destination dir where the source files are copied to. Defaults to .\\jar.'
  }
  def options = cli.parse(args)
  if(!options || options.h || args.length == 0) {
    cli.usage()
    return
  }
  def extraArgs = options.arguments()
  File currdir = new File(".")
  String basedir = currdir.getAbsolutePath()
  String destdir = Paths.get(currdir.getAbsolutePath(),"jar").toAbsolutePath()

  if(options.b) basedir = options.b
  if(options.d) destdir = options.d
  JOptProcessor jop = new JOptProcessor(basedir:basedir,destdir:destdir,optfiles:extraArgs)
  jop.processFiles()
  jop.createJavaPackage()
}

process(args)

