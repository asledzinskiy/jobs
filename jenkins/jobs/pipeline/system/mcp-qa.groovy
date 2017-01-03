git = new com.mirantis.mcp.Git()

node('calico'){
    try {

      stage ('Checkout mcp-qa'){
        git.gerritPatchsetCheckout {
          credentialsId = "mcp-ci-gerrit"
          withWipeOut = true
        }
      }

      stage ('Run syntax tests') { sh "tox -v"  }

    }
    catch(err) {
      echo "Failed: ${err}"
      currentBuild.result = 'FAILURE'
    }
}

