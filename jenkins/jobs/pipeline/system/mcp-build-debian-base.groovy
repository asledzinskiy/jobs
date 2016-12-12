def docker_dev_repo = "docker-dev-local"
def docker_prod_repo = "docker-prod-local"
def namespace = 'mirantis/base-images'
def image_name = 'debian-base'
def artifactory = new com.mirantis.mcp.MCPArtifactory()
def common = new com.mirantis.mcp.Common()
def imageTag = common.getDatetime()
def docker_registry = env.DEBIAN_DOCKER_REGISTRY
def gerrit_host = env.GERRIT_HOST
def artifactoryServer = Artifactory.server('mcp-ci')
def artifactory_url = artifactoryServer.getUrl()

stage('build-debian-image') {
    node('k8s') {
        if ( ! docker_registry ) {
            error('DEBIAN_DOCKER_REGISTRY must be set')
        }
        def docker_image = "${namespace}/${image_name}"
        deleteDir()
        gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = "${gerrit_host}"
            project = "mcp-ci/mcp-cicd-installer"
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
                sh "docker tag mcp-ci-debian-base:latest ${docker_registry}/${docker_image}:${imageTag}"
                sh "docker push ${docker_registry}/${docker_image}:${imageTag}"
            }
            // copy to docker_prod_repo with the same tag
            artifactory.promoteDockerArtifact(artifactory_url,
                                        docker_dev_repo,
                                        docker_prod_repo,
                                        docker_image,
                                        imageTag,
                                        imageTag,
                                        true)
            // move to docker_prod_repo with the 'latest' tag
            artifactory.promoteDockerArtifact(artifactory_url,
                                        docker_dev_repo,
                                        docker_prod_repo,
                                        docker_image,
                                        imageTag,
                                        'latest')
        }
    }
}
