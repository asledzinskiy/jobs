def gitTools = new com.mirantis.mcp.Git()
def ciTools = new com.mirantis.mcp.Common()

ARTIFACTORY_SERVER = Artifactory.server("mcp-ci")
ARTIFACTORY_URL = ARTIFACTORY_SERVER.getUrl()
ARTIFACTORY_PYPI_URL = "${ARTIFACTORY_URL}/api/pypi/pypi-virtual/simple/"


node("whale") {
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
                project = "ceph/whale"
            }
        }
    }

    withEnv(["PIP_INDEX_URL=${ARTIFACTORY_PYPI_URL}"]) {
        stage("Run static checker for Python2.7") {
            ciTools.runTox "py27-static_check"
        }

        stage("Run static checker for Python3.5") {
            ciTools.runTox "py35-static_check"
        }

        stage("Run steps checker for Python2.7") {
            ciTools.runTox "py27-steps-checker"
        }
    }
}
