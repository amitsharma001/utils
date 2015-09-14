# Source global definitions
if [ -f /etc/bashrc ]; then
	. /etc/bashrc
fi

function append_path() {
	if ! echo ${PATH} |grep -q $1 ; then
		export PATH=$1:$PATH
	fi
}

# User specific aliases and functions
append_path /sbin
append_path /usr/sbin
append_path ~/bin
append_path ~/tools

#change these if you don't dig my colors!
NM="\[\033[0;1;37m\]" #means no background and wihite lines
USR="\[\033[0;1;36m\]" #user name
HI="\[\033[0;1;33m\]" #change this for host letter colors
CD="\[\033[0;37m\]" #change command: color
SI="\[\033[0;31m\]" #this is for the current directory
NI="\[\033[0;1;30m\]" #for @ symbol
IN="\[\033[0m\]"
                                                                                                                          
PS1="$NM-=[$USR\u$NI@$HI\h $SI\w$NM]=-\n${CD}command: $NM> $IN"
export PS1 
#export JAVA_HOME