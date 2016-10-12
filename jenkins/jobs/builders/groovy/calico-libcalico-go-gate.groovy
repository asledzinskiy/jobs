node('calico'){

  // Get Artifactory server instance, defined in the Artifactory Plugin administration page.
  def server = Artifactory.server("mcp-ci")

  def ARTIFACTORY_URL = "https://artifactory.mcp.mirantis.net/artifactory/projectcalico"

  try {

    gitSSHCheckout {
      credentialsId = "mcp-ci-gerrit"
      branch = "mcp"
      host = "review.fuel-infra.org"
      project = "projectcalico/libcalico-go"
    }

    def gitCommit = sh(returnStdout: true, script: "git -C ${WORKSPACE} rev-parse --short HEAD").trim()
    def BUILD_ID = "mcp-${gitCommit}"


    stage ('Running libcalico-go unittests') {
      sh "make test-containerized"
    }


    stage ('Build libcalico-go') {
      sh "make build-containerized"
    }


    stage('Publishing libcalico-go artifacts') {
      dir("artifacts"){

        sh "echo "

        // Save Image name ID
        writeFile file: "lastbuild", text: "${BUILD_ID}"
        // Create the upload spec.

        def uploadSpec = """{
            "files": [
                    {
                        "pattern": "**",
                        "target": "projectcalico/mcp/libcalico-go/"
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
