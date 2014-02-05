#!/bin/bash

basedir=$PWD
output_path="/home/etienne/workspace"

pathToScripts=$PWD/scripts
host=*****
user=*****
pathToScriptsOnRemoteServer=/home/tigeorgia/shenmartav/sqlscripts

if ([ -f "$basedir/insertParentLaws.sql" ]); then
    rm $basedir/insertParentLaws.sql
fi

if ([ -f "$basedir/insertChildLaw.sql" ]); then
    rm $basedir/insertChildLaw.sql
fi

if ([ -f "$basedir/insertDraftLaws.sql" ]); then
    rm $basedir/insertDraftLaws.sql
fi

java -jar ./draftlawParser.jar $output_path

if ([ -f "$basedir/insertDraftLaws.sql" ]); then
    scp $output_path/insertDraftLaws.sql $user@$host:$pathToScriptsOnRemoteServer/insertDraftLaws.sql
    ssh $user@$host 'bash -s' < runDraftlawFile.sh
fi

