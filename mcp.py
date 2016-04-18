import argparse
from string import split
from os.path import basename, dirname, exists, isfile, isdir
import os
import shutil
import sys
from glob import glob

parser = argparse.ArgumentParser()
parser.add_argument("template",help="A template with file copy instruactions. Each line in the file should be of the form source|destination.")
parser.add_argument("-v","--verbose", help="Print the file actions as they take place.", action='store_true')
parser.add_argument("-w","--whatif", help="Just print the file actions, dont execute.", action='store_true')
parser.add_argument("-b","--basedir", help="Base directory. If base directory is specified all relative paths are considered relative to base dir.")
parser.add_argument("-r","--reverse", help="Move the files in the opposite direction from destination to source.", action='store_true')

args = parser.parse_args()

instructions = []
processed = []

def process(src,dest, reverse=False):
  try:
    if (args.basedir != None and args.basedir != ''):
        if(not os.path.isabs(src)): src = os.path.join(args.basedir,src)
        if(not os.path.isabs(dest)): dest = os.path.join(args.basedir,dest)
    comments = []
    if not reverse: comments.append("\nProcessing %s -> %s\n"%(src,dest))
    else: comments.append("Processing %s -> %s"%(dest,src))
    if (exists(dest) and (not isdir(dest))): raise Exception('The destination %s must be a directory.'%dest)
    if not exists(dest):
        comments.append("Creating Directory: %s"%dest)
        if not args.whatif: os.makedirs(dest)
    odest = dest
    if reverse: dest = dirname(src)
    files = glob(src)
    for f in files:
      nf = os.path.normpath(f)
      if nf in processed: continue
      else: processed.append(nf)
      if reverse: f = os.path.join(odest, basename(f))
      comments.append("Copying %s -> %s"%(f,dest))
      if (not args.whatif): shutil.copy(f,dest)
  finally:      
    if (args.verbose or args.whatif):
        for line in comments: print(line)

if not exists(args.template): sys.exit("The file %s does not exist."%args.template)
with open(args.template) as f:
    instructions = [line.strip() for line in f.readlines() ]

if args.reverse: instructions.reverse()   
for instr in instructions:
    try:
        if (len(instr) == 0 or instr.startswith('#')): continue
        src, dest = [x.strip() for x in split(instr,'|',2)]
        if(isdir(src) and (not src.endswith(os.sep))): src += os.sep
        process(src,dest, args.reverse)
    except Exception as e:
        print("Error: %s\nError: %s"%(instr,e))

    
