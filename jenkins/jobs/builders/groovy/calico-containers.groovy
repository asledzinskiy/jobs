node('calico'){

  // Get Artifactory server instance, defined in the Artifactory Plugin administration page.
  def server = Artifactory.server("mcp-ci")

  def DOCKER_REPO = "artifactory.mcp.mirantis.net:5001"
  def ARTIFACTORY_URL = "https://artifactory.mcp.mirantis.net/artifactory/projectcalico"

  def NODE_IMAGE = "calico/node"
  def NODE_IMAGE_TAG = "v0.20.0"
  def NODE_NAME = "${DOCKER_REPO}/${NODE_IMAGE}:${NODE_IMAGE_TAG}"

  def CTL_IMAGE = "calico/ctl"
  def CTL_IMAGE_TAG = "v0.20.0"
  def CTL_NAME = "${DOCKER_REPO}/${CTL_IMAGE}:${CTL_IMAGE_TAG}"

  // calico/build goes from {ARTIFACTORY_URL}/mcp/libcalico/
  def BUILD_NAME = "${ARTIFACTORY_URL}/mcp/libcalico/lastbuild".toURL().text.trim()
  // calico/felix goes from {ARTIFACTORY_URL}/mcp/felix/
  def FELIX_NAME = "${ARTIFACTORY_URL}/mcp/felix/lastbuild".toURL().text.trim()

  def CONFD_BUILD = "${ARTIFACTORY_URL}/mcp/confd/lastbuild".toURL().text.trim()
  def CONFD_URL = "${ARTIFACTORY_URL}/mcp/confd/confd-${CONFD_BUILD}"

  def BIRD_BUILD="${ARTIFACTORY_URL}/mcp/calico-bird/lastbuild".toURL().text.trim()
  def BIRD_URL="${ARTIFACTORY_URL}/mcp/calico-bird/bird-${BIRD_BUILD}"
  def BIRD6_URL="${ARTIFACTORY_URL}/mcp/calico-bird/bird6-${BIRD_BUILD}"
  def BIRDCL_URL="${ARTIFACTORY_URL}/mcp/calico-bird/birdcl-${BIRD_BUILD}"

  try {

    stage ('Checkout calico-containers'){
      gerritPatchsetCheckout {
        credentialsId = "mcp-ci-gerrit"
        withWipeOut = true
      }
    }

    def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE} rev-parse --short HEAD").trim()
    def BUILD = "${GERRIT_CHANGE_NUMBER}-${gitCommit}"


    stage ('Run unittest') {
      sh "make ut"
    }


    stage ('Build calico/ctl image'){
      sh """
        make ctl_image \
          CTL_CONTAINER_NAME=${CTL_NAME}-${BUILD} \
          BUILD_CONTAINER_NAME=${BUILD_NAME} \
          BIRDCL_URL=${BIRDCL_URL}
      """
    }


    stage('Build calico/node'){
      sh """
        make node_image \
          NODE_CONTAINER_NAME=${NODE_NAME}-${BUILD} \
          BUILD_CONTAINER_NAME=${BUILD_NAME} \
          FELIX_CONTAINER_NAME=${FELIX_NAME} \
          CONFD_URL=${CONFD_URL} \
          BIRD_URL=${BIRD_URL} \
          BIRD6_URL=${BIRD6_URL} \
          BIRDCL_URL=${BIRDCL_URL}
      """
    }


    stage('Publishing containers artifacts'){

      withCredentials([
        [$class: 'UsernamePasswordMultiBinding',
          credentialsId: 'artifactory',
          passwordVariable: 'ARTIFACTORY_PASSWORD',
          usernameVariable: 'ARTIFACTORY_LOGIN']
      ]) {
        sh """
          echo 'Pushing images'
          docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${DOCKER_REPO}
          docker push ${NODE_NAME}-${BUILD}
          docker push ${CTL_NAME}-${BUILD}
        """
      }

      dir("artifacts"){
        CALICO_NODE_IMAGE_REPO="${DOCKER_REPO}/${NODE_IMAGE}"
        CALICOCTL_IMAGE_REPO="${DOCKER_REPO}/${CTL_IMAGE}"
        CALICO_VERSION="${NODE_IMAGE_TAG}-${BUILD}"
        // Save the last build ID
        writeFile file: "lastbuild", text: "${BUILD}"
        // Create config yaml for Kargo
        writeFile file: "calico-containers-${BUILD}.yaml",
                  text: """\
                    calico_node_image_repo: ${CALICO_NODE_IMAGE_REPO}
                    calicoctl_image_repo: ${CALICOCTL_IMAGE_REPO}
                    calico_version: ${CALICO_VERSION}
                  """.stripIndent()
        // Create the upload spec.
        def uploadSpec = """{
            "files": [
                    {
                        "pattern": "**",
                        "target": "projectcalico/${GERRIT_CHANGE_NUMBER}/calico-containers/"
                    }
                ]
            }"""

        // Upload to Artifactory.
        def buildInfo1 = server.upload(uploadSpec)
      } // dir artifacts
    } //stage

    stage ("Run system tests") {
       build job: 'calico.system-test.deploy', propagate: true, wait: true, parameters:
        [
            [$class: 'StringParameterValue', name: 'CALICO_NODE_IMAGE_REPO', value: CALICO_NODE_IMAGE_REPO],
            [$class: 'StringParameterValue', name: 'CALICOCTL_IMAGE_REPO', value: CALICOCTL_IMAGE_REPO],
            [$class: 'StringParameterValue', name: 'CALICO_VERSION', value: CALICO_VERSION],
            [$class: 'StringParameterValue', name: 'MCP_BRANCH', value: 'mcp'],
        ]
    }

  }
  catch(err) {
    echo "Failed: ${err}"
    currentBuild.result = 'FAILURE'
  }
  finally {
    // fix workspace owners
    sh "sudo chown -R jenkins:jenkins ${WORKSPACE}"
  }
}
