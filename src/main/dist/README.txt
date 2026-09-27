Novel-Grabber-Claude
=============

Downloads web novels and saves them as EPUB, PDF or text.


Requirements
------------
Java 25 or newer. Check with "java -version" in a terminal.
Download Java from https://adoptium.net/ if needed.


Installing
----------
Unzip this folder somewhere you can write to, for example your Documents folder.
Don't put it in "C:\Program Files": Novel-Grabber-Claude saves its settings next to
Novel-Grabber-Claude.jar and could not write them there.


Starting
--------
Windows:        double-click Novel-Grabber-Claude.bat
Linux / macOS:  ./novel-grabber-claude.sh

Both open the program window. With arguments they run the command line version:

    Novel-Grabber-Claude.bat -link https://host.com/novel/ -chapters 1 5
    ./novel-grabber-claude.sh -link https://host.com/novel/ -chapters 1 5

Command line downloads are saved in the current folder unless you pass -path.

If Windows says the file is from the internet and blocks it: right-click the
downloaded zip, choose Properties, tick "Unblock", then unzip it again.


Files
-----
Novel-Grabber-Claude.jar   the program
sources\            the supported sites; keep it next to the jar

Novel-Grabber-Claude creates these next to the jar:
config.ini, library.json, accounts.json   settings, library and site logins
Novels\                                    library novels (EPUBs and covers), unless
                                           you choose another save location
log.txt                                    error log; include it when reporting a problem


Updating
--------
Replace Novel-Grabber-Claude.jar and the sources folder with the ones from the new zip.
Keep config.ini, library.json, accounts.json and the Novels folder.
