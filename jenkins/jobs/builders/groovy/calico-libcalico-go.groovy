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
    def BUILD_ID = "${GERRIT_CHANGE_NUMBER}-${gitCommit}"


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
                        "target": "sandbox/${GERRIT_CHANGE_NUMBER}/libcalico-go/"
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
