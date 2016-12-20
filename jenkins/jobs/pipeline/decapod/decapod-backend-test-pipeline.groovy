def gitTools = new com.mirantis.mcp.Git()
def ciTools = new com.mirantis.mcp.Common()

ARTIFACTORY_SERVER = Artifactory.server("mcp-ci")
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
ARTIFACTORY_PYPI_URL = "${ARTIFACTORY_URL}/api/pypi/pypi-virtual/simple/"


node("decapod") {
    stage("Checkout SCM") {
        if (env.GERRIT_EVENT_TYPE) {
            gitTools.gerritPatchsetCheckout {
                credentialsId = "mcp-ci-gerrit"
            }
        } else {
            def gerritHost = env.GERRIT_HOST
            gitTools.gitSSHCheckout {
                credentialsId = "mcp-ci-gerrit"
                branch="master"
                host = gerritHost
                project = "ceph/decapod"
            }
        }
    }

    withEnv(["PIP_INDEX_URL=${ARTIFACTORY_PYPI_URL}"]) {
        stage("Run tests") {
            try {
                ciTools.runTox "jenkins-test"
            } catch (InterruptedException exc) {
                echo "The job was aborted"
            } finally {
                archiveArtifacts artifacts: 'htmlcov/**', excludes: null
                junit keepLongStdio: true, testResults: 'test-results.xml'
            }
        }

        stage("Run linters") {
            ciTools.runTox "jenkins-static"
        }

        stage("Run code complexity validation") {
            ciTools.runTox "metrics"
        }

        stage("Run Ansible linters") {
            ciTools.runTox "devenv-lint"
        }

        stage("Run dead code validation (optional)") {
            ciTools.runTox "deadcode"
        }

        stage("Run Bandit (optional)") {
            ciTools.runTox "bandit"
        }
    }
}
