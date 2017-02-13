def makeTempVenvDir() {
  tempDir = sh(script: 'mktemp -d', returnStdout: true).trim()
  sh(script: "virtualenv ${tempDir}")
  return tempDir
}

def installTestrailReporter(String directory) {
  dir(directory) {
    sh(script: ". bin/activate && pip install xunit2testrail")
  }
}

def report(String venvDir, reportfile, pasteUrl, testrailUrl, testrailUser, testrailPassword, envDescription,
           testrailSuite, testrailMilestone, testrailProject, bvtBuildId, bvtType, tempestBuildId) {

  testrailPlanName = testrailMilestone + ' Tempest ' + 'BVT-' + bvtType + ' ' + '#' + bvtBuildId
  buildUrl = tempestJobUrl + tempestBuildId + '/'
  dir(venvDir) {
    sh(script: ". bin/activate && report -v --testrail-plan-name \"${testrailPlanName}\" --env-description \"${envDescription}\" " +
            "--testrail-url ${testrailUrl} --testrail-user ${testrailUser} --testrail-password ${testrailPassword} " +
            "--testrail-project \"${testrailProject}\" --testrail-milestone \"${testrailMilestone}\" --testrail-suite \"${testrailSuite}\" " +
            "--test-results-link ${buildUrl} --paste-url ${pasteUrl} " +
            "--testrail-name-template \"{title}\" --xunit-name-template \"{classname}.{methodname}\" --send-skipped ${reportfile}")
  }
}

def saveTempestReport(String venvDir) {
  report = httpRequest("http://static.mcp.mirantis.net/tempest/${tempestBuildId}/result.xml").getContent().replaceAll("class_name", "classname")
  dir(venvDir) {
    writeFile(file: "report.xml", text: report)
  }
}

def removeTempDir(String venvDir) {
  sh(script: "rm -rf ${venvDir}")
}

node('ccp-docker-build') {
  testrailUrl = env.TESTRAIL_URL
  testrailSuite = env.TESTRAIL_SUITE
  testrailMilestone = env.TESTRAIL_MILESTONE
  testrailProject = env.TESTRAIL_PROJECT
  bvtBuildId = env.BVT_BUILD_ID
  bvtType = env.BVT_TYPE
  tempestBuildId = env.TEMPEST_BUILD_ID
  tempestStaticUrl = env.TEMPEST_STATIC_URL
  tempestJobUrl = env.TEMPEST_JOB_URL
  pasteUrl = env.PASTE_URL
  envDescription = env.TEST_ENVIRONMENT

  try {
    stage('Prepare virtualenv for reporter') {
      virtualEnv = makeTempVenvDir()

      installTestrailReporter(virtualEnv)
    }

    stage('Save report and upload it') {
      saveTempestReport(virtualEnv)
      withCredentials([
              [$class          : 'UsernamePasswordMultiBinding',
               credentialsId   : 'mcp-qa-testrail',
               passwordVariable: 'TESTRAIL_PASSWORD',
               usernameVariable: 'TESTRAIL_LOGIN']
      ]) {
        testrailUser = env.TESTRAIL_LOGIN
        testrailPassword = env.TESTRAIL_PASSWORD
        report(virtualEnv, 'report.xml', pasteUrl, testrailUrl, testrailUser, testrailPassword, envDescription,
                testrailSuite, testrailMilestone, testrailProject, bvtBuildId, bvtType, tempestBuildId)
      }
    }
  }
  catch (Exception x) {
    echo x.getMessage()
    currentBuild.result = 'FAILURE'
  }
  finally {
    removeTempDir(virtualEnv)
  }
}