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

        CALICO_CNI_DOWNLOAD_URL="${ARTIFACTORY_URL}/${GERRIT_CHANGE_NUMBER}/calico-cni/calico-${CNI_BUILD}"
        CALICO_CNI_CHECKSUM=sh(returnStdout: true, script: "sha256sum calico-${CNI_BUILD} | cut -d' ' -f1").trim()
        CALICO_CNI_IPAM_DOWNLOAD_URL="${ARTIFACTORY_URL}/${GERRIT_CHANGE_NUMBER}/calico-cni/calico-ipam-${CNI_BUILD}"
        CALICO_CNI_IPAM_CHECKSUM=sh(returnStdout: true, script: "sha256sum calico-ipam-${CNI_BUILD} | cut -d' ' -f1").trim()

        // Save Image name ID
        writeFile file: "lastbuild", text: "${CNI_BUILD}"
        // Create the upload spec.
        writeFile file: "calico-cni-${CNI_BUILD}.yaml",
                  text: """\
                    calico_cni_download_url: ${CALICO_CNI_DOWNLOAD_URL}
                    calico_cni_checksum: ${CALICO_CNI_CHECKSUM}
                    calico_cni_ipam_download_url: ${CALICO_CNI_IPAM_DOWNLOAD_URL}
                    calico_cni_ipam_checksum: ${CALICO_CNI_IPAM_CHECKSUM}
                  """.stripIndent()

        def uploadSpec = """{
            "files": [
                    {
                        "pattern": "**",
                        "target": "projectcalico/${GERRIT_CHANGE_NUMBER}/calico-cni/"
                    }
                ]
            }"""

        // Upload to Artifactory.
        def buildInfo1 = server.upload(uploadSpec)
      }
    }

    stage ("Run system tests") {
       build job: 'calico.system-test.deploy', propagate: true, wait: true, parameters:
        [
            [$class: 'StringParameterValue', name: 'CALICO_CNI_DOWNLOAD_URL', value: CALICO_CNI_DOWNLOAD_URL],
            [$class: 'StringParameterValue', name: 'CALICO_CNI_CHECKSUM', value: CALICO_CNI_CHECKSUM],
            [$class: 'StringParameterValue', name: 'CALICO_CNI_IPAM_DOWNLOAD_URL', value: CALICO_CNI_IPAM_DOWNLOAD_URL],
            [$class: 'StringParameterValue', name: 'CALICO_CNI_IPAM_CHECKSUM', value: CALICO_CNI_IPAM_CHECKSUM],
            [$class: 'StringParameterValue', name: 'OVERWRITE_HYPERKUBE_CNI', value: 'true'],
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
