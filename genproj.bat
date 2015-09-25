
@REM  CreateProject.bat
@echo OFF
SET Provider=%1
SET BASEPROD=ProviderBase

IF "%1" == "" GOTO USAGE

IF NOT EXIST C:\dev\RSSBus\trunk\Release\%Provider%\jdbc GOTO ERROR1
cd C:\dev\RSSBus\trunk\Release\%Provider%\jdbc
CALL groovy C:\Software\Tools\utils\javasrc.groovy %2 -d C:\JavaProjects\%Provider% ..\..\..\%BASEPROD%\javabaseopts.txt
CALL groovy C:\Software\Tools\utils\javasrc.groovy %2 -d C:\JavaProjects\%Provider% ..\..\..\%BASEPROD%\oputilsjavaopts.txt
CALL groovy C:\Software\Tools\utils\javasrc.groovy %2 -d C:\JavaProjects\%Provider% ..\..\..\%BASEPROD%\jdbccoreopts.txt
CALL groovy C:\Software\Tools\utils\javasrc.groovy %2 -d C:\JavaProjects\%Provider% ..\..\..\ProviderSalesforceV4\javaopts.txt
CALL groovy C:\Software\Tools\utils\javasrc.groovy %2 -d C:\JavaProjects\%Provider% ..\..\..\RSSBusMySQL\javaopts.txt
CALL groovy C:\Software\Tools\utils\javasrc.groovy %2 -d C:\JavaProjects\%Provider% classpath.txt
CALL groovy C:\Software\Tools\utils\javasrc.groovy %2 -d C:\JavaProjects\%Provider% jdbc.txt
exit /B 1

:ERROR1
echo %Provider% does not exist.
exit /B 1

:USAGE
echo %0 ProviderDir [-r]
