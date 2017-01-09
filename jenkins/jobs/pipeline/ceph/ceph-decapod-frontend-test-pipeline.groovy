def gitTools = new com.mirantis.mcp.Git()
def ciTools = new com.mirantis.mcp.Common()

ARTIFACTORY_SERVER = Artifactory.server("mcp-ci")
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
ARTIFACTORY_NPM_REGISTRY_URL = ""
LOCAL_IMAGE = 'decapod/ui-tests'
TEST_IMAGE = "${env.DOCKER_REGISTRY}/mirantis/ceph/${LOCAL_IMAGE}"


node("decapod") {
    deleteDir()

    stage("Checkout SCM") {
        gitTools.gerritPatchsetCheckout {
            credentialsId = "mcp-ci-gerrit"
        }
    }

    try {
        stage("Pull latest UI image for tests") {
            sh "docker pull ${TEST_IMAGE}:latest"
            sh "docker tag ${TEST_IMAGE}:latest ${LOCAL_IMAGE}:latest"
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
        sh "docker rmi -f ${LOCAL_IMAGE} || true"
        sh "sudo chown -R jenkins:jenkins ${env.WORKSPACE}"
        archiveArtifacts artifacts: 'ui/coverage/**', excludes: null
        junit keepLongStdio: true, testResults: 'ui/test-results.xml'
    }

}
