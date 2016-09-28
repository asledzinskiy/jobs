def docker_dev_repo = "docker-dev-local"
def docker_prod_repo = "docker-prod-local"
def namespace = 'mirantis/base-images'
def image_name = 'debian-base'
def tools = new ci.mcp.Tools()
def docker_registry = env.DEBIAN_DOCKER_REGISTRY
def gerrit_host = env.GERRIT_HOST
def artifactory_url = env.ARTIFACTORY_URL

stage('build-debian-image') {
    node('k8s') {
        if ( ! docker_registry ) {
            error('DEBIAN_DOCKER_REGISTRY must be set')
        }
        if ( ! artifactory_url ) {
            error('ARTIFACTORY_URL must be set')
        }
        def docker_image = "${namespace}/${image_name}"
        deleteDir()
        gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = "${gerrit_host}"
            project = "mcp-ci/mcp-cicd-poc"
        }
        withEnv(["VENV_PATH=${env.WORKSPACE}/.tox/mcp-ci",
                 "DOCKER_NET=mcp-ci-net",
                 "TEST_MODE=true"]) {
            sh 'tox -e mcp-ci'
            sh '''. ${VENV_PATH}/bin/activate
                docker network ls | grep -q ${DOCKER_NET} || \
                  docker network create -d bridge --subnet 172.30.0.0/24 ${DOCKER_NET}
                ./mcp-ci.sh init-config
                ./mcp-ci.sh build debian-base'''
            withCredentials([
                [$class: 'UsernamePasswordMultiBinding',
                 credentialsId: 'artifactory',
                 passwordVariable: 'ARTIFACTORY_PASSWORD',
                 usernameVariable: 'ARTIFACTORY_LOGIN']
            ]) {
                sh 'docker login -u ${ARTIFACTORY_LOGIN} -p ${ARTIFACTORY_PASSWORD} ${DEBIAN_DOCKER_REGISTRY}'
                sh "docker tag mcp-ci-debian-base:latest ${docker_registry}/${docker_image}:latest"
                sh "docker push ${docker_registry}/${docker_image}:latest"
            }
            tools.promoteDockerArtifact(artifactory_url,
                                        docker_dev_repo,
                                        docker_prod_repo,
                                        docker_image,
                                        'latest',
                                        'latest')
        }
    }
}
