node('calico'){

  // Get Artifactory server instance, defined in the Artifactory Plugin administration page.
  // TODO(skulanov) use sandbox
  //def server = Artifactory.server("mcp-ci")
  def server = Artifactory.newServer url: "https://artifactory.mcp.mirantis.net/artifactory", username: "sandbox", password: "sandbox"

  def ARTIFACTORY_URL = "https://artifactory.mcp.mirantis.net/artifactory/sandbox"

  try {

    gerritPatchsetCheckout {
      credentialsId = "mcp-ci-gerrit"
      withWipeOut = true
    }

    def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE} rev-parse --short HEAD").trim()
    def CNI_BUILD = "${GERRIT_CHANGE_NUMBER}-${gitCommit}"


    // TODO(skulanov) GO_CONTAINER_NAME should be defined some arti image
    stage ('Build calico-cni') {
      sh "make build-containerized"
    }


    stage('Publishing cni artifacts') {
      dir("artifacts"){

        sh """
          cp ${WORKSPACE}/dist/calico calico-${CNI_BUILD}
          cp ${WORKSPACE}/dist/calico-ipam calico-ipam-${CNI_BUILD}
        """

        def calico_cni_checksum = sh(returnStdout: true, script: "sha256sum calico-${CNI_BUILD} | cut -d' ' -f1").trim()
        def calico_cni_ipam_checksum = sh(returnStdout: true, script: "sha256sum calico-ipam-${CNI_BUILD} | cut -d' ' -f1").trim()

        // Save Image name ID
        writeFile file: "lastbuild", text: "${CNI_BUILD}"
        // Create the upload spec.
        writeFile file: "calico-cni-${CNI_BUILD}.yaml",
                  text: """\
                    calico_cni_download_url: ${ARTIFACTORY_URL}/${GERRIT_CHANGE_NUMBER}/calico-cni/calico-${CNI_BUILD}
                    calico_cni_checksum: ${calico_cni_checksum}
                    calico_cni_ipam_download_url: ${ARTIFACTORY_URL}/${GERRIT_CHANGE_NUMBER}/calico-cni/calico-ipam-${CNI_BUILD}
                    calico_cni_ipam_checksum: ${calico_cni_ipam_checksum}
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
