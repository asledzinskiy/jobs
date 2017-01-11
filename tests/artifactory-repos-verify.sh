#!/bin/bash -e

shopt -s nullglob

echo $1
topdir=$1
repodir="${topdir}/artifactory/repositories"
schemadir="${topdir}/artifactory/schema"

for repoconfig in $(ls ${repodir})
do
    case ${repoconfig} in
        *-local.json ) json validate --schema-file=${schemadir}/local.json --document-file=${repodir}/${repoconfig} ;;
	*-remote.json ) json validate --schema-file=${schemadir}/remote.json --document-file=${repodir}/${repoconfig} ;;
	*-virtual.json ) json validate --schema-file=${schemadir}/virtual.json --document-file=${repodir}/${repoconfig} ;;
	* ) echo ${repoconfig}: Unsupported repository naming scheme ;;
    esac
done
