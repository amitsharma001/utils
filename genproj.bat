
@REM  CreateProject.bat
@echo OFF
SET Provider=%1
SET BASEPROD=ProviderBase

IF "%1" == "" GOTO USAGE

IF NOT EXIST C:\dev\RSSBus\trunk\v5\%Provider%\jdbc GOTO TRUNK
cd C:\dev\RSSBus\v5\Release\%Provider%\jdbc
GOTO GROOVE

:TRUNK
IF NOT EXIST C:\dev\RSSBus\trunk\Release\%Provider%\jdbc GOTO ERROR1
cd C:\dev\RSSBus\trunk\Release\%Provider%\jdbc
GOTO GROOVE

:GROOVE

IF NOT "%2" == "" GOTO GROOVEP
echo "NOOOOOOOOO"
@RMDIR /S /Q C:\JavaProjects\%Provider%\src

:GROOVEP
CALL groovy D:\Software\Tools\utils\javasrc.groovy %2 -d D:\JavaProjects\%Provider% ..\..\..\%BASEPROD%\javabaseopts.txt
CALL groovy D:\Software\Tools\utils\javasrc.groovy %2 -d D:\JavaProjects\%Provider% ..\..\..\%BASEPROD%\oputilsjavaopts.txt
CALL groovy D:\Software\Tools\utils\javasrc.groovy %2 -d D:\JavaProjects\%Provider% ..\..\..\%BASEPROD%\jdbccoreopts.txt
CALL groovy D:\Software\Tools\utils\javasrc.groovy %2 -d D:\JavaProjects\%Provider% ..\..\..\%BASEPROD%\javaremotingopts.txt
CALL groovy D:\Software\Tools\utils\javasrc.groovy %2 -d D:\JavaProjects\%Provider% ..\..\..\%Provider%\javaopts.txt
CALL groovy D:\Software\Tools\utils\javasrc.groovy %2 -d D:\JavaProjects\%Provider% ..\..\..\RSSBusMySQL\javaopts.txt
CALL groovy D:\Software\Tools\utils\javasrc.groovy %2 -d D:\JavaProjects\%Provider% classpath.txt
CALL groovy D:\Software\Tools\utils\javasrc.groovy %2 -d D:\JavaProjects\%Provider% jdbc.txt
exit /B 1

:ERROR1
echo %Provider% does not exist.
exit /B 1

:USAGE
echo %0 ProviderDir [-r]
