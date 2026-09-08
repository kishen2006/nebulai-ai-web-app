@echo off
set MAVEN_OPTS=-Djava.net.preferIPv4Stack=true %MAVEN_OPTS%
"%~dp0tools\apache-maven-3.9.9\bin\mvn.cmd" %*
