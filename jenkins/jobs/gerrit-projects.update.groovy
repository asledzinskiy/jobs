node('tools') {

    stage('Code checkout') {
        def HOST = env.GERRIT_HOST
        gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = HOST
            project = "mcp-ci/project-config"
        }
    }

    stage('Gerrit update') {
        env.PROJECT_CONFIG_PATH = "${WORKSPACE}"
        sh 'jeepyb-manage-projects.sh'
    }

}
