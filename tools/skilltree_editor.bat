@echo off
rem ---------------------------------------------------------------------------
rem  Naruto-Mod - skill tree editor
rem
rem  Opens the offline skill tree board in the browser. Drag nodes around, edit
rem  their cards, then press "save" - the page POSTs the whole table to this
rem  little server, which writes tmp\skilltree-edits.json. After that tell the
rem  agent "apply the edits" and it patches Skill.java + the language files.
rem
rem  The page is the editor: nodes snap to the grid, occupied cells are
rem  refused, Ctrl+Z undoes, Ctrl+S saves. The lint under the board runs live.
rem
rem  Close this window (or press Ctrl+C) when done - the page keeps working,
rem  but "save" only works while this server is running.
rem
rem  Usage:
rem      skilltree_editor.bat            serve on 8765 and open the browser
rem      skilltree_editor.bat 9100       serve on another port
rem      python tools\skilltree_preview.py --check       lint only
rem      python tools\skilltree_preview.py --apply       write the saved edits back
rem ---------------------------------------------------------------------------

setlocal
title Naruto-Mod skill tree editor
cd /d "%~dp0.."

set "PORT=%~1"
if "%PORT%"=="" set "PORT=8765"

where python >nul 2>&1
if errorlevel 1 (
	echo.
	echo [ERROR] python was not found in PATH.
	echo.
	pause
	exit /b 1
)

echo Serving the skill tree editor on http://127.0.0.1:%PORT%/skilltree-preview.html
echo Press Ctrl+C in this window to stop it.
echo.

python "tools\skilltree_preview.py" --serve %PORT% --open
set "CODE=%ERRORLEVEL%"

if not "%CODE%"=="0" (
	echo.
	echo Editor exited with code %CODE%.
	pause
)

exit /b %CODE%
