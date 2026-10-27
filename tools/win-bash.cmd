@echo off
REM Locate Git Bash for just recipes on Windows (avoid WSL's System32\bash.exe).
setlocal
set "BASH="
if exist "D:\env\Git\bin\bash.exe" set "BASH=D:\env\Git\bin\bash.exe"
if not defined BASH if exist "%ProgramFiles%\Git\bin\bash.exe" set "BASH=%ProgramFiles%\Git\bin\bash.exe"
if not defined BASH if exist "%ProgramFiles(x86)%\Git\bin\bash.exe" set "BASH=%ProgramFiles(x86)%\Git\bin\bash.exe"
if not defined BASH if exist "%LOCALAPPDATA%\Programs\Git\bin\bash.exe" set "BASH=%LOCALAPPDATA%\Programs\Git\bin\bash.exe"
if not defined BASH (
  echo Git Bash not found. Install Git for Windows, or set tools\win-bash.cmd to your bash.exe. >&2
  exit /b 1
)
for %%I in ("%BASH%") do set "GIT_BIN=%%~dpI"
set "PATH=%GIT_BIN%;%GIT_BIN%..\usr\bin;%PATH%"
REM Prefer real CPython ahead of WindowsApps python3 store stub.
if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" (
  set "PATH=%LOCALAPPDATA%\Programs\Python\Python312;%LOCALAPPDATA%\Programs\Python\Python312\Scripts;%PATH%"
)
"%BASH%" %*
