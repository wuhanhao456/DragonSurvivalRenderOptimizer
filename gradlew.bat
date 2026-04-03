@rem
@rem                          Version 1.0, January 2022
@rem
@rem   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION
@rem
@rem   The author and owner of Dragon Survival is Viktoria Ershova (BlackAures1)
@rem
@rem   Allowed use of content: private use, modification, distribution, view and fork repository,
@rem     making pull request or suggestions to improve the project. Other uses are negotiated with the author separately.
@rem     The author retains full rights to the project. Only "Minecraft Mod: Dragons Survival"
@rem     participants are free to make changes may freely modify, distribute and use all project
@rem     content except for the creation of third-party projects using any Dragon Survival content.
@rem     List of participants "Minecraft Mod: Dragons Survival" on the GitHub:
@rem                https://github.com/orgs/DragonSurvivalTeam/people
@rem
@rem    This License does not grant permission to use the trade
@rem      names, trademarks, service marks, or product names of the Licensor,
@rem      except as required for reasonable and customary use in describing the
@rem      origin of the Work and reproducing the content of the NOTICE file.
@rem
@rem    Disclaimer of Warranty. Unless required by applicable law or
@rem      agreed to in writing, Licensor provides the Work (and each
@rem      Contributor provides its Contributions) on an "AS IS" BASIS,
@rem      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
@rem      implied, including, without limitation, any warranties or conditions
@rem      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
@rem     PARTICULAR PURPOSE. You are solely responsible for determining the
@rem     appropriateness of using or redistributing the Work and assume any
@rem      risks associated with Your exercise of permissions under this License.
@rem
@rem    Limitation of Liability. In no event and under no legal theory,
@rem      whether in tort (including negligence), contract, or otherwise,
@rem      unless required by applicable law (such as deliberate and grossly
@rem      negligent acts) or agreed to in writing, shall any Contributor be
@rem      liable to You for damages, including any direct, indirect, special,
@rem      incidental, or consequential damages of any character arising as a
@rem      result of this License or out of the use or inability to use the
@rem      Work (including but not limited to damages for loss of goodwill,
@rem      work stoppage, computer failure or malfunction, or any and all
@rem      other commercial damages or losses), even if such Contributor
@rem      has been advised of the possibility of such damages.
@rem
@rem   END OF TERMS AND CONDITIONS
@rem
@rem   Copyright 2020-2023 Black Aures
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmovement.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
