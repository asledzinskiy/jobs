#!/bin/bash

set -ex

GITHEAD=$(git rev-parse HEAD)
JOBS_OUT_DIR=${WORKSPACE}/output/jobs
JOBS_LOGFILE=${JOBS_OUT_DIR}/jobs-diff.log
RESULT=''

# First generate output from BASE_COMMIT vars value
git checkout "${BASE_COMMIT}"
tox -e jjb-generate -- old

# Then use that as a reference to compare against HEAD
git checkout "${GITHEAD}"
tox -e jjb-generate -- new

compare_xml() {
    # Replace arguments with built-in variables ($1 - path to jobs or views output directory, $2 - path to jobs or views log file)
    OUT_DIR=$1
    LOGFILE=$2
    # Specifying for http links type of comaprison (jobs or views)
    TYPE=$3

    CHANGE=0
    ADD=0
    REMOVE=0

    CHANGED="[changed]<br>"
    ADDED="[added]<br>"
    REMOVED="[removed]<br>"

    DIFF=$(diff -q -r -u "${OUT_DIR}/old" "${OUT_DIR}/new" &>"${LOGFILE}"; echo "${?}")

    # Any changed job discovered? If exit code was 1, then there is a difference
    if [[ ${DIFF} -eq 1 ]]; then

      # Loop through all changed jobs
      for JOB in $(awk '/Files/ {print $2}' "${LOGFILE}"); do

        # Extract job's name
        JOB_NAME=$(basename "${JOB}")

        # Make diff
        diff -U 50 "${OUT_DIR}/old/${JOB_NAME}" \
            "${OUT_DIR}/new/${JOB_NAME}" >> "${OUT_DIR}/diff/${JOB_NAME}" || true

        CHANGE=1
        CHANGED+="<a href=${BUILD_URL}artifact/output/${TYPE}/diff//${JOB_NAME}/*view*/>${JOB_NAME}</a><br>"
      done

      # Now find added/removed Jobs...
      for JOB in $(awk '/Only in/ {print $3$4}' "${LOGFILE}"); do
        ON=$(echo "${JOB}"|awk -F/ 'split($8,a,":") {print a[1]}')
        JOB_NAME=$(echo  "${JOB}"| awk -F: '{print $2}')
        if [[ ${ON} = 'old' ]]; then
          REMOVE=1
          REMOVED+="<a href=${BUILD_URL}artifact/output/${TYPE}/old//${JOB_NAME}/*view*/>${JOB_NAME}</a><br>"
        elif [[ ${ON} = 'new' ]]; then
          ADD=1
          ADDED+="<a href=${BUILD_URL}artifact/output/${TYPE}/new//${JOB_NAME}/*view*/>${JOB_NAME}</a><br>"
        fi
      done

    fi

    # Add section only if there're any changes found
    if [ "$(( CHANGE + ADD + REMOVE ))" -gt 0 ]; then
      RESULT+="<br><b>$(tr "[:lower:]" "[:upper:]" <<< "${TYPE}"):</b><br>"
    fi

    # Print Changed jobs.
    if [[ ${CHANGE} -eq 1 ]]; then
      RESULT+=${CHANGED}
    fi
    # And print added/removed if any.
    if [[ ${REMOVE} -eq 1 ]]; then
      RESULT+=${REMOVED}
    fi
    if [[ ${ADD} -eq 1 ]]; then
      RESULT+=${ADDED}
    fi
}

compare_xml "${JOBS_OUT_DIR}" "${JOBS_LOGFILE}" "jobs"

echo "${RESULT#<br>}"
