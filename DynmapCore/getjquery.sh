#!/usr/bin/env sh
cwd=$(dirname "${0}")
wget -q -O - https://ajax.googleapis.com/ajax/libs/jquery/1.11.0/jquery.js > "$cwd"/src/main/resources/extracted/web/js/jquery-1.11.0.js