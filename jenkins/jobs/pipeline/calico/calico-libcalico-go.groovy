node('calico'){

  try {

    def HOST = env.GERRIT_HOST

    stage ('Checkout libcalico-go'){
      if ( env.GERRIT_EVENT_TYPE == 'patchset-created' ) {
          gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
            withWipeOut = true
          }
      } else {
          gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "mcp"
            host = HOST
            project = "projectcalico/libcalico-go"
          }
      } // else
    }

    stage ('Running libcalico-go unittests') {
      sh "make test-containerized"
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
