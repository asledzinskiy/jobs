node('calico'){

  try {

    def tools = new ci.mcp.Tools()

    def server = Artifactory.server("mcp-ci")
    def artifactoryUrl = "https://artifactory.mcp.mirantis.net/artifactory/projectcalico"

    def buildInfo = Artifactory.newBuildInfo()
    buildInfo.env.capture = true
    buildInfo.env.filter.addInclude("*")
    buildInfo.env.collect()

    def currentBuildId = ""

    def HOST = env.GERRIT_HOST
    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "mcp"
      host = HOST
      project = "projectcalico/calico-cni"
    }

    dir("${env.WORKSPACE}/tmp_libcalico-go") {

      if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
        currentBuildId = env.GERRIT_CHANGE_NUMBER

        stage ('Checkout libcalico-go'){
          gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
            withWipeOut = true
          }
        }
      } else {
        currentBuildId = "mcp"

        stage ('Checkout libcalico-go'){
          gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "mcp"
            host = HOST
            project = "projectcalico/libcalico-go"
          }
        }
      } // else

      stage ('Running libcalico-go unittests') {
        sh "make test-containerized"
      }

    }

    def gitCommit = tools.getGitCommit().take(7)
    def cniBuildId = "${currentBuildId}-${gitCommit}"

    // TODO(skulanov) GO_CONTAINER_NAME should be defined some arti image
    stage ('Build calico-cni') {
      // TODO(apanchenko): replace `sed` by Yaml.load() -> modify map -> Yaml.dump()
      sh """
        sed -e '/^- name: .*\\/libcalico-go\$/{n;s/version:.*\$/repo: file:\\/\\/\\/go\\/src\\/github.com\\/projectcalico\\/libcalico-go\\n  vcs: git/;}' -i.bak glide.lock
        grep -qP '.*repo:\\s+file:.*libcalico-go' glide.lock || { echo 1>&2 \'Repository (libcalico-go) path was not properly set in glide.lock!'; exit 1; }
        """
      sh "LIBCALICOGO_PATH=${WORKSPACE}/tmp_libcalico-go make build-containerized"
    }

    stage('Publishing cni artifacts') {
      dir("artifacts"){

        sh """
          cp ${WORKSPACE}/dist/calico calico-${cniBuildId}
          cp ${WORKSPACE}/dist/calico-ipam calico-ipam-${cniBuildId}
        """

        CALICO_CNI_DOWNLOAD_URL="${artifactoryUrl}/${currentBuildId}/calico-cni/calico-${cniBuildId}"
        CALICO_CNI_CHECKSUM=sh(returnStdout: true, script: "sha256sum calico-${cniBuildId} | cut -d' ' -f1").trim()
        CALICO_CNI_IPAM_DOWNLOAD_URL="${artifactoryUrl}/${currentBuildId}/calico-cni/calico-ipam-${cniBuildId}"
        CALICO_CNI_IPAM_CHECKSUM=sh(returnStdout: true, script: "sha256sum calico-ipam-${cniBuildId} | cut -d' ' -f1").trim()

        // Save Image name ID
        writeFile file: "lastbuild", text: "${cniBuildId}"
        // Create the upload spec.
        writeFile file: "calico-cni-${cniBuildId}.yaml",
                  text: """\
                    calico_cni_download_url: ${CALICO_CNI_DOWNLOAD_URL}
                    calico_cni_checksum: ${CALICO_CNI_CHECKSUM}
                    calico_cni_ipam_download_url: ${CALICO_CNI_IPAM_DOWNLOAD_URL}
                    calico_cni_ipam_checksum: ${CALICO_CNI_IPAM_CHECKSUM}
                  """.stripIndent()

        def properties = tools.getBinaryBuildProperties()

        def uploadSpec = """{
            "files": [
                    {
                        "pattern": "**",
                        "target": "projectcalico/${currentBuildId}/calico-cni/",
                        "props": "${properties}"
                    }
                ]
            }"""

        // Upload to Artifactory.
        server.upload(uploadSpec, buildInfo)
      }
    } // publishing cni artifacts

    // publish buildInfo
    server.publishBuildInfo buildInfo

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
