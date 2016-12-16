def gitTools = new com.mirantis.mcp.Git()
node('infra-tools') {

    stage('Code checkout') {
        def HOST = env.GERRIT_HOST
        gitTools.gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = HOST
            project = "mcp-ci/project-config"
        }
    }

    stage('Gerrit update') {
        env.PROJECT_CONFIG_PATH = "${env.WORKSPACE}"
        sh 'jeepyb-manage-projects.sh'
    }

}
