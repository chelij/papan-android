@echo off
setlocal
set PAPAN_GRADLE_ROOT=%~dp0
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -classpath "%PAPAN_GRADLE_ROOT%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain -p "%PAPAN_GRADLE_ROOT%" %*
) else (
  java -classpath "%PAPAN_GRADLE_ROOT%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain -p "%PAPAN_GRADLE_ROOT%" %*
)
endlocal
