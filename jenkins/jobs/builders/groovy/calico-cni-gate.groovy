node('calico'){

  // Get Artifactory server instance, defined in the Artifactory Plugin administration page.
  def server = Artifactory.server("mcp-ci")

  def ARTIFACTORY_URL = "https://artifactory.mcp.mirantis.net/artifactory/projectcalico"

  try {

    gerritPatchsetCheckout {
      credentialsId = "mcp-ci-gerrit"
      withWipeOut = true
    }

    def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE} rev-parse --short HEAD").trim()
    def CNI_BUILD = "mcp-${gitCommit}"


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
                    calico_cni_download_url: ${ARTIFACTORY_URL}/mcp/calico-cni/calico-${CNI_BUILD}
                    calico_cni_checksum: ${calico_cni_checksum}
                    calico_cni_ipam_download_url: ${ARTIFACTORY_URL}/mcp/calico-cni/calico-ipam-${CNI_BUILD}
                    calico_cni_ipam_checksum: ${calico_cni_ipam_checksum}
                  """.stripIndent()

        def uploadSpec = """{
            "files": [
                    {
                        "pattern": "**",
                        "target": "projectcalico/mcp/calico-cni/"
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
