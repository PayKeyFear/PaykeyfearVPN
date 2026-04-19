@rem Gradle start-up script for Windows.
@rem Thin shim around the Gradle wrapper bootstrap.
@if "%DEBUG%" == "" @echo off
@rem -----------------------------------------------------------------------------

setlocal enabledelayedexpansion

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.\
set APP_HOME=%DIRNAME%
set WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
  echo gradle-wrapper.jar not found at %WRAPPER_JAR% 1>&2
  echo Run 'gradle wrapper --gradle-version 8.10.2' once to fetch it. 1>&2
  exit /b 1
)

if defined JAVA_HOME goto findJavaFromJavaHome
set JAVA_EXE=java.exe
goto execute

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

:execute
"%JAVA_EXE%" "-Dorg.gradle.appname=%~nx0" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
