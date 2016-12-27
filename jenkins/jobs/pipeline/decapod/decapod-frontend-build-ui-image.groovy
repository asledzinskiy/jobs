def gitTools = new com.mirantis.mcp.Git()
def artifactory = new com.mirantis.mcp.MCPArtifactory()

ARTIFACTORY_SERVER = Artifactory.server("mcp-ci")
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
UBUNTU_REPO_URL = "${ARTIFACTORY_URL}/ubuntu-virtual"
ARTIFACTORY_NPM_REGISTRY_URL = ""
IMAGE_NAMESPACE = "mirantis/ceph"
IMAGE_NAME = "decapod/ui-tests"
DEV_REPOSITORY = "docker-dev-local"
PROD_REPOSITORY = "docker-prod-local"


node("decapod") {
    stage("Checkout SCM") {
        def gerritHost = env.GERRIT_HOST
        gitTools.gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = gerritHost
            project = "ceph/decapod"
        }
    }

    stage("Build image") {
        sh 'make copy_example_keys'
        writeFile file: 'ubuntu_apt.list', text: """\
            deb ${UBUNTU_REPO_URL} xenial main restricted universe multiverse
            deb ${UBUNTU_REPO_URL} xenial-updates main restricted universe multiverse
            deb ${UBUNTU_REPO_URL} xenial-backports main restricted universe multiverse
            deb ${UBUNTU_REPO_URL} xenial-security main restricted universe multiverse
        """.stripIndent()
        withEnv(["NPM_REGISTRY_URL=${ARTIFACTORY_NPM_REGISTRY_URL}"]) {
            sh 'make build_container_ui_tests'
        }
    }

    stage("Upload image") {
        def imageRegistryName = "${IMAGE_NAMESPACE}/${IMAGE_NAME}"

        sh "docker tag ${IMAGE_NAME}:latest ${env.DOCKER_REGISTRY}/${imageRegistryName}:latest"
        artifactory.uploadImageToArtifactory(
            ARTIFACTORY_SERVER,
            env.DOCKER_REGISTRY,
            imageRegistryName,
            'latest',
            DEV_REPOSITORY)
        sh "docker rmi -f ${env.DOCKER_REGISTRY}/${imageRegistryName}:latest || true"
        sh "docker rmi -f ${IMAGE_NAME}:latest || true"
    }
}
