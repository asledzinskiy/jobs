ciTools = new com.mirantis.mcp.Common()
git = new com.mirantis.mcp.Git()

node('verify-tests'){
    try {

      stage ('Checkout source code'){
        git.gerritPatchsetCheckout ([
          credentialsId : "mcp-ci-gerrit",
          withWipeOut : true
        ])
      }

      stage ('Run syntax tests') { ciTools.runTox("pep8") }

      stage ('Run unit tests (py27)') { ciTools.runTox("py27") }

    }
    catch(err) {
      echo "Failed: ${err}"
      currentBuild.result = 'FAILURE'
    }
}
