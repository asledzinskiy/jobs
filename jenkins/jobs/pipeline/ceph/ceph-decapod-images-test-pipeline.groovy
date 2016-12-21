def gitTools = new com.mirantis.mcp.Git()
def artifactory = new com.mirantis.mcp.MCPArtifactory()

ARTIFACTORY_SERVER = Artifactory.server("mcp-ci")
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
ARTIFACTORY_PYPI_URL = "${ARTIFACTORY_URL}/api/pypi/pypi-virtual/simple/"
DEBIAN_REPO_URL = "${ARTIFACTORY_URL}/debian-virtual"
UBUNTU_REPO_URL = "${ARTIFACTORY_URL}/ubuntu-virtual"
ARTIFACTORY_NPM_REGISTRY_URL = ""
// Decapod make builds images with tags 'decapod/base' etc.
IMAGE_NAMESPACE = "mirantis/ceph"
DEV_REPOSITORY = "docker-dev-local"


if (!env.DOCKER_REGISTRY) {
    error("DOCKER_REGISTRY must be set")
}

node("decapod") {
    stage("Checkout SCM") {
        gitTools.gerritPatchsetCheckout ([
            credentialsId : "mcp-ci-gerrit"
        ])
    }

    def tagVersion = 'latest'
    if (env.GERRIT_BRANCH != 'master') {
        tagVersion = gitTools.getGitDescribe(true).replaceAll(/-.*$/, '')
    }
    tagVersion = "${tagVersion}-${env.GERRIT_CHANGE_NUMBER}"
    echo "Build with tag ${tagVersion}"

    stage("Build images") {
        sh "make copy_example_keys"

        writeFile file: 'ubuntu_apt.list', text: """\
            deb ${UBUNTU_REPO_URL} xenial main restricted universe multiverse
            deb ${UBUNTU_REPO_URL} xenial-updates main restricted universe multiverse
            deb ${UBUNTU_REPO_URL} xenial-backports main restricted universe multiverse
            deb ${UBUNTU_REPO_URL} xenial-security main restricted universe multiverse
        """.stripIndent()
        writeFile file: 'debian_apt.list', text: """\
            deb ${DEBIAN_REPO_URL} jessie main
            deb ${DEBIAN_REPO_URL} jessie-updates main
            deb ${DEBIAN_REPO_URL} jessie-backports main
        """.stripIndent()

        withEnv([
            "PIP_INDEX_URL=${ARTIFACTORY_PYPI_URL}",
            "NPM_REGISTRY_URL=${ARTIFACTORY_NPM_REGISTRY_URL}",
            "IMAGE_VERSION=${tagVersion}"
        ]) {
            sh "make build_containers"
        }
    }

    stage("Upload to development registry") {
        def images = ["base", "base-plugins", "api", "controller", "cron",
                      "db-data", "db", "frontend", "migrations"];
        for (image in images) {
            def imageName = "decapod/${image}"
            def imageRegistryName = "${IMAGE_NAMESPACE}/${imageName}"

            sh "docker tag ${imageName}:${tagVersion} ${env.DOCKER_REGISTRY}/${imageRegistryName}:${tagVersion}"
            artifactory.uploadImageToArtifactory(
                ARTIFACTORY_SERVER,
                env.DOCKER_REGISTRY,
                imageRegistryName,
                tagVersion,
                DEV_REPOSITORY)
            sh "docker rmi -f ${env.DOCKER_REGISTRY}/${imageRegistryName}:${tagVersion} || true"
            sh "docker rmi -f ${imageName}:${tagVersion} || true"
            sh "docker rmi -f ${imageName}:latest || true"
        }
    }
}
