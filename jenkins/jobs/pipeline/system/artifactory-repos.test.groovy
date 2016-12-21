def ciTools = new com.mirantis.mcp.Common()
def gitTools = new com.mirantis.mcp.Git()

node('tools') {

    stage('Code checkout') {
        gitTools.gerritPatchsetCheckout ([
            credentialsId : "mcp-ci-gerrit"
        ])
    }

    withEnv(["VENV_PATH=${env.WORKSPACE}/.tox/artifactory-repos"]) {
        stage('Validate repository configs') {
            ciTools.runTox("artifactory-repos-verify")
        }

    }
}
