import csv
import subprocess
import sys
from optparse import OptionParser

commandTemplate = ["ls","%3","%2"]

parser = OptionParser()
parser.add_option("-f","--file", dest="datafile",
                  help="The data file to get the data for the command.", metavar="DATA_FILE")
parser.add_option("-w", "--whatif",
                  action="store_true", dest="whatif", default=False,
                  help="If whatif is set, the script outputs the command instead of executing it.")

(options, args) = parser.parse_args()

def process_command(command):
  if(options.whatif): print " ".join(command)
  else: 
    print("Executing: %s"%" ".join(command))
    output = ''
    try:
      output = subprocess.check_call(command, stderr=subprocess.STDOUT, shell=True, universal_newlines=True)
    except subprocess.CalledProcessError as exc:
      #It would be better to print output but that works in version 2.7 and above only.
      #print("Status: FAIL",exc.returncode, exc.output)
      print("Status: FAIL",exc.returncode)
    else:
      print("Status: SUCCESS")
      print(output)
    print("***********************************")

def process_file(fname):
  with open(fname, 'r') as f:
    reader = csv.reader(f, dialect='excel', delimiter=',')
    for row in reader:
      command = []
      for arg in commandTemplate:
        if arg.startswith('%'):
          index = int(arg[1:]) 
          if len(row) < index:
            print("There is no value for parameter %d in data file %s. Continuing with next row."%(index,datafile))
          else:
            command.append(row[index-1])
        else:
          command.append(arg)
      process_command(command)

if(len(args) > 0 and options.datafile == None): options.datafile = args[0]
if(options.datafile == None): sys.exit("The data file must be specified.")

process_file(options.datafile)
