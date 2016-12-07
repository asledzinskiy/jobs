if (!env.DOCKER_REPOSITORY) {
    error('DOCKER_REPOSITORY must be set')
}

if (!env.DOCKER_REGISTRY) {
    error('DOCKER_REGISTRY must be set')
}

node('k8s') {
    deleteDir()
    def gerritHost = env.GERRIT_HOST
    stage('prepare mcp-cicd-installer') {
        gitSSHCheckout {
            credentialsId = 'mcp-ci-gerrit'
            host = gerritHost
            branch = 'master'
            project = 'mcp-ci/mcp-cicd-installer'
        }
    }

    withEnv(["VENV_PATH=${env.WORKSPACE}/.tox/mcp-ci",
             "TEST_MODE=true"]) {
        stage('prepare virtual env') {
            sh 'tox -e mcp-ci'
        }

        stage('run build images') {
            withCredentials([
                    [$class: 'UsernamePasswordMultiBinding',
                     credentialsId: 'artifactory',
                     passwordVariable: 'ARTIFACTORY_PASSWORD',
                     usernameVariable: 'ARTIFACTORY_LOGIN']
            ]) {
                sh("docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${env.DOCKER_REGISTRY}")
            }
            sh './mcp-ci.sh init-config'
            sh '''
               for image in base unit integration; do
                   # copy test conf file as it's required but not used
                   ./mcp-ci.sh build k8s-tests-${image}
                   docker tag k8s-tests-${image}:latest ${DOCKER_REGISTRY}/${DOCKER_REPOSITORY}/k8s-tests-${image}:latest
                   docker push ${DOCKER_REGISTRY}/${DOCKER_REPOSITORY}/k8s-tests-${image}:latest
               done
            '''
        }
    }
}