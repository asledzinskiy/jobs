def artifactory = new com.mirantis.mcp.MCPArtifactory()
def server = Artifactory.server('mcp-ci')

if (!env.DOCKER_REGISTRY) {
    error('DOCKER_REGISTRY must be set')
}

node('k8s') {
    deleteDir()
    def gerritHost = env.GERRIT_HOST
    def imageNamespace = 'mirantis/k8s-tests-images'
    def dockerDevRepo = "docker-dev-local"
    def dockerProdRepo = "docker-prod-local"
    def dockerRegistry = env.DOCKER_REGISTRY
    def artifactoryUrl = server.getUrl()
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
            def images = [ 'base', 'unit', 'integration' ]
            withCredentials([
                    [$class: 'UsernamePasswordMultiBinding',
                     credentialsId: 'artifactory',
                     passwordVariable: 'ARTIFACTORY_PASSWORD',
                     usernameVariable: 'ARTIFACTORY_LOGIN']
            ]) {
                sh("docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${dockerRegistry}")
            }
            sh './mcp-ci.sh init-config'

            for(int i = 0; i < images.size(); i++){
                def image = "k8s-tests-${images[i]}"
                sh "./mcp-ci.sh build ${image}"
                sh "docker tag ${image}:latest ${dockerRegistry}/${imageNamespace}/${image}:latest"
                sh "docker push ${dockerRegistry}/${imageNamespace}/${image}:latest"
                artifactory.promoteDockerArtifact(artifactoryUrl,
                                        dockerDevRepo,
                                        dockerProdRepo,
                                        "${imageNamespace}/${image}",
                                        'latest',
                                        'latest')
                sh "docker rmi -f ${dockerRegistry}/${imageNamespace}/${image}:latest || true"
            }
        }
    }
}
