def ciTools = new com.mirantis.mcp.Common()
def gitTools = new com.mirantis.mcp.Git()
def server = Artifactory.server('mcp-ci')
def artifactoryUrl = server.getUrl()

node('tools') {

    stage('Code checkout') {
        def HOST = env.GERRIT_HOST
        gitTools.gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = HOST
            project = "mcp-ci/project-config"
        }
    }

    withEnv(["VENV_PATH=${env.WORKSPACE}/.tox/artifactory-repos-update",
             "ARTIFACTORY_URL=${artifactoryUrl}"]) {
        stage('Prepare virtual env') {
            ciTools.runTox("artifactory-repos-update")
        }

        stage('Update repositories') {
            withCredentials([
                    [$class: 'UsernamePasswordMultiBinding',
                     credentialsId: 'artifactory',
                     passwordVariable: 'ARTIFACTORY_PASSWORD',
                     usernameVariable: 'ARTIFACTORY_LOGIN']
            ]) {
                sh '''
                bash -c "source ${VENV_PATH}/bin/activate && ar_too --url ${ARTIFACTORY_URL} --username ${ARTIFACTORY_LOGIN} --password ${ARTIFACTORY_PASSWORD} configure --repos_dir ./artifactory/repositories"
                '''
               }

        }
    }
}
