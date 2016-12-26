def gitTools = new com.mirantis.mcp.Git()
def ciTools = new com.mirantis.mcp.Common()

ARTIFACTORY_SERVER = Artifactory.server("mcp-ci")
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
ARTIFACTORY_NPM_REGISTRY_URL = ""
TEST_IMAGE = "${env.DOCKER_REGISTRY}/mirantis/ceph/decapod/ui-tests"


node("decapod") {
    stage("Checkout SCM") {
        gitTools.gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
        }
    }

    try {
        stage("Pull latest UI image for tests") {
            sh "docker pull ${TEST_IMAGE}:latest"
        }

        stage("Run tests") {
            withEnv(["NPM_REGISTRY_URL=${ARTIFACTORY_NPM_REGISTRY_URL}"]) {
                sh 'make run_container_ui_tests'
            }
        }
    } catch (err) {
        echo "Error during execution."
        currentBuild.result = 'FAILURE'
    } finally {
        sh "docker rmi -f ${TEST_IMAGE} || true"
    }

}
