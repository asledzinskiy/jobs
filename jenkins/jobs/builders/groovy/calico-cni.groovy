def tools = new ci.mcp.Tools()

node('calico'){

  // Get Artifactory server instance, defined in the Artifactory Plugin administration page.
  // TODO(skulanov) use sandbox
  //def server = Artifactory.server("mcp-ci")
  def server = Artifactory.newServer url: "https://artifactory.mcp.mirantis.net/artifactory", username: "sandbox", password: "sandbox"

  def ARTIFACTORY_URL = "https://artifactory.mcp.mirantis.net/artifactory/sandbox"
  def LIBCALICO_DOCKER_IMAGE = "${ARTIFACTORY_URL}/mcp/libcalico/lastbuild".toURL().text.trim()
  def DOCKER_REPO = "artifactory.mcp.mirantis.net:5004"

  def NODE_IMAGE_TAG = "v0.20.0"
  def CTL_IMAGE = "calico/ctl"

  try {

    gerritPatchsetCheckout {
      credentialsId = "mcp-ci-gerrit"
      withWipeOut = true
    }

    def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE} rev-parse --short HEAD").trim()
    def CNI_BUILD = "${GERRIT_CHANGE_NUMBER}-${gitCommit}"


    stage ('Build calico-cni') {
      sh """
        docker run --rm \
          -v ${WORKSPACE}:/code ${LIBCALICO_DOCKER_IMAGE} \
          sh -c \
          \"pip install pykube && pyinstaller calico.py -ayF && pyinstaller ipam.py -ayF -n calico-ipam\"
      """
    }


    stage('Publishing cni artifacts') {
      dir("artifacts"){

        sh """
          cp ${WORKSPACE}/dist/calico calico-${CNI_BUILD}
          cp ${WORKSPACE}/dist/calico-ipam calico-ipam-${CNI_BUILD}
        """

        // Save Image name ID
        writeFile file: "lastbuild", text: "${CNI_BUILD}"
        // Create the upload spec.
        writeFile file: "calico-cni-${CNI_BUILD}.yaml",
                  text: """\
                    calico_cni_download_url: ${ARTIFACTORY_URL}/${GERRIT_CHANGE_NUMBER}/calico-cni/calico-${CNI_BUILD}
                    calicoctl_image_repo: ${DOCKER_REPO}/${CTL_IMAGE}
                    calico_version: ${NODE_IMAGE_TAG}-${CNI_BUILD}
                  """.stripIndent()
        def uploadSpec = """{
            "files": [
                    {
                        "pattern": "**",
                        "target": "sandbox/${GERRIT_CHANGE_NUMBER}/calico-cni/"
                    }
                ]
            }"""

        // Upload to Artifactory.
        def buildInfo1 = server.upload(uploadSpec)
      }
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
