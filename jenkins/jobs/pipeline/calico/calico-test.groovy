calico = new com.mirantis.mcp.Calico()
qaCommon = new com.mirantis.mcp_qa.Common()

def testContainers(){
  dir("${env.WORKSPACE}/calicoctl") {
    calico.checkoutCalico([
      project_name : 'calicoctl',
      //FIXME(apanchenko): use env.CALICOCTL_COMMIT here when CR#1479 is merged to upstream
      commit : "FETCH_HEAD",
      host : env.GERRIT_HOST,
      credentialsId: 'mcp-ci-gerrit',
      //FIXME: remove this
      refspec: 'refs/changes/79/1479/3'
    ])

    //FIXME: remove this
    sh "git rebase origin/mcp"

    calico.systestCalico(
      "${env.VIRTUAL_PROD_DOCKER_REGISTRY}/${env.PROJECT_NAMESPACE}/calico/node:latest",
      "${env.VIRTUAL_PROD_DOCKER_REGISTRY}/${env.PROJECT_NAMESPACE}/calico/ctl:latest",
      false
      )
    sh "cp nosetests.xml ${WORKSPACE}/calico.st.nosetests.xml"
  }
}


def testCni(){
  dir("${env.WORKSPACE}/cni-plugin") {
    calico.checkoutCalico([
      project_name : 'cni-plugin',
      //FIXME(apanchenko): use env.CNI_PLUGIN_COMMIT here when CR#1125 and CR#1983 are merged to upstream
      commit : "FETCH_HEAD",
      host : env.GERRIT_HOST,
      credentialsId: 'mcp-ci-gerrit',
      //FIXME: remove this
      refspec: 'refs/changes/83/1983/1'
    ])

    //FIXME: remove this
    sh "git rebase origin/mcp"

    calico.testCniPlugin()
    sh "cp junit.xml ${WORKSPACE}/calico.cni.junit.xml"
  }
}


def uploadResults(){
  stage('Upload tests results'){
    def thisBuildUrl = "${env.JENKINS_URL}/job/${JOB_NAME}/${BUILD_NUMBER}/"
    def testPlanName = "${env.TESTRAIL_MILESTONE} Component-${new Date().format('yyyy-MM-dd')}"

    qaCommon.uploadResultsTestRail([
      junitXml: "${WORKSPACE}/calico.st.nosetests.xml",
      testPlanName: testPlanName,
      testSuiteName: "${env.TESTRAIL_TEST_SUITE}",
      testrailMilestone: "${env.TESTRAIL_MILESTONE}",
      jobURL: thisBuildUrl,
    ])

    return qaCommon.uploadResultsTestRail([
      junitXml: "${WORKSPACE}/calico.cni.junit.xml",
      testPlanName: testPlanName,
      testSuiteName: "${env.TESTRAIL_TEST_SUITE}",
      testrailMilestone: "${env.TESTRAIL_MILESTONE}",
      jobURL: thisBuildUrl,
    ])
  }
}


node ('calico') {
  try {
    testContainers()
    testCni()
    testRunUrl = uploadResults()

    currentBuild.description = """
    <a href="${testRunUrl}">TestRail report</a>
    """
  }
  catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }
  finally {
    calico.calicoFixOwnership()
    archiveArtifacts allowEmptyArchive: true, artifacts: 'calico.st.nosetests.xml,calico.cni.junit.xml', excludes: null
    junit keepLongStdio: false, testResults: 'calico.st.nosetests.xml,calico.cni.junit.xml'
  }
}