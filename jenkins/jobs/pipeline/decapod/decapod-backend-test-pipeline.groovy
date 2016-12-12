def gitTools = new com.mirantis.mcp.Git()
def ciTools = new com.mirantis.mcp.Common()

ARTIFACTORY_SERVER = Artifactory.server("mcp-ci")
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
ARTIFACTORY_PYPI_URL = "${ARTIFACTORY_URL}/pypi-virtual"
GERRIT_CREDENTIALS = "mcp-ci-gerrit"
PROJECT = "ceph/decapod"
GERRIT_HOST = env.GERRIT_HOST


node("decapod") {
    stage("Checkout SCM") {
        if (env.GERRIT_EVENT_TYPE) {
            gitTools.gerritPatchsetCheckout {
                credentialsId = GERRIT_CREDENTIALS
            }
        } else {
            gitTools.gitSSHCheckout {
                credentialsId = GERRIT_CREDENTIALS
                branch="master"
                host = GERRIT_HOST
                project = PROJECT
            }
        }
    }

    stage("Run tests") {
        withEnv(["PIP_INDEX_URL=${ARTIFACTORY_PYPI_URL}"]) {
            try {
                ciTools.runTox "jenkins-test"
            } catch (InterruptedException exc) {
                echo "The job was aborted"
            } finally {
                archiveArtifacts artifacts: 'htmlcov/**', excludes: null
                junit keepLongStdio: true, test_results: "${env.WORKSPACE}/test-results.xml"
            }
        }
    }

    stage("Run linters") {
        withEnv(["PIP_INDEX_URL=${ARTIFACTORY_PYPI_URL}"]) {
            ciTools.runTox "jenkins-static"
        }
    }

    stage("Run code complexity validation") {
        withEnv(["PIP_INDEX_URL=${ARTIFACTORY_PYPI_URL}"]) {
            ciTools.runTox "metrics"
        }
    }

    stage("Run Ansible linters") {
        withEnv(["PIP_INDEX_URL=${ARTIFACTORY_PYPI_URL}"]) {
            ciTools.runTox "devenv-lint"
        }
    }

    stage("Run dead code validation (optional)") {
        withEnv(["PIP_INDEX_URL=${ARTIFACTORY_PYPI_URL}"]) {
            ciTools.runTox "deadcode"
        }
    }

    stage("Run Bandit (optional)") {
        withEnv(["PIP_INDEX_URL=${ARTIFACTORY_PYPI_URL}"]) {
            ciTools.runTox "bandit"
        }
    }
}
