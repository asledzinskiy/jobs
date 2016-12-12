def gitTools = new com.mirantis.mcp.Git()
def commonTools = new com.mirantis.mcp.Common()

node('verify-tests') {

    stage('Code checkout') {
        gitTools.gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
        }
    }

    stage('Jeepyb Verify') {
        commonTools.runTox 'jeepyb-verify'
    }

}
