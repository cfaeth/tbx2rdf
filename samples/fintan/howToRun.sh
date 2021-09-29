#!/bin/bash

HOME=`echo $0 | sed -e s/'^[^\/]*$'/'.'/g -e s/'\/[^\/]*$'//`;
ROOT=$HOME/../..;


$ROOT/run.sh -c samples/fintan/tbx2rdf-bigFile.json


