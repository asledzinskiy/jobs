node('verify-tests') {

    stage('Code checkout') {
        gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
        }
    }

    stage('Jeepyb Verify') {
        runTox 'jeepyb-verify'
    }

}
