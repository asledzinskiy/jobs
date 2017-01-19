gitTools = new com.mirantis.mcp.Git()
commonTools = new com.mirantis.mcp.Common()
test = env.TEST_TYPE

if ( test == 'linters' ) {
    run_linters()
} else if ( test == 'validate' ) {
    run_ccp_validate()
} else {
    error('Unknown or missing test name')
}


def run_linters () {
    node('verify-tests') {

        stage('Code checkout') {
            deleteDir()
            gitTools.gerritPatchsetCheckout([
                    credentialsId: "mcp-ci-gerrit"
            ])
        }

        stage('Run linters') {
            commonTools.runTox 'linters'
        }
    }
}

def run_ccp_validate () {

    def CURRENT_PROJECT = "${env.GERRIT_PROJECT}".split("/")[-1]
    def PROJECTS = [ "artifactory", "debian-base", "entrypoint", "etcd",
                     "gerrit", "jenkins", "mariadb" ]

    node('ccp-docker-build') {
        def WORKSPACE = env.WORKSPACE
        def CONFIG_FILE="${WORKSPACE}/config.yaml"

        stage('Code checkout') {
            deleteDir()
            gitTools.gitSSHCheckout ([
                    credentialsId : "mcp-ci-gerrit",
                    branch : "master",
                    host : "${GERRIT_HOST}",
                    project : "ccp/fuel-ccp",
                    targetDir : "fuel-ccp"
            ])
            for (PROJECT in PROJECTS) {
                gitTools.gitSSHCheckout ([
                        credentialsId : "mcp-ci-gerrit",
                        branch : "master",
                        host : "${GERRIT_HOST}",
                        project : "ccp/fuel-ccp-${PROJECT}",
                        targetDir : "fuel-ccp-${PROJECT}"
                ])
            }
            dir("fuel-ccp-${CURRENT_PROJECT}"){
                gitTools.gerritPatchsetCheckout([
                        credentialsId: "mcp-ci-gerrit"
                ])
            }
        }

        stage('Execute ccp validate') {
            writeFile file: CONFIG_FILE, text: """\
                debug: True
                repositories:
                  skip_empty: True
                  repos:
                  - git_url: https://gerrit.mcp.mirantis.net/ccp/fuel-ccp-artifactory
                    name: fuel-ccp-artifactory
                  - git_url: https://gerrit.mcp.mirantis.net/ccp/fuel-ccp-debian-base
                    name: fuel-ccp-debian-base
                  - git_url: https://gerrit.mcp.mirantis.net/ccp/fuel-ccp-entrypoint
                    name: fuel-ccp-entrypoint
                  - git_url: https://gerrit.mcp.mirantis.net/ccp/fuel-ccp-etcd
                    name: fuel-ccp-etcd
                  - git_url: https://gerrit.mcp.mirantis.net/ccp/fuel-ccp-gerrit
                    name: fuel-ccp-gerrit
                  - git_url: https://gerrit.mcp.mirantis.net/ccp/fuel-ccp-jenkins
                    name: fuel-ccp-jenkins
                  - git_url: https://gerrit.mcp.mirantis.net/ccp/fuel-ccp-mariadb
                    name: fuel-ccp-mariadb
                  clone: False
                  path: "${WORKSPACE}"
            """.stripIndent()
            dir("fuel-ccp"){
                commonTools.runTox "venv -- ccp --config-file ${CONFIG_FILE} config dump"
                commonTools.runTox "venv -- ccp --config-file ${CONFIG_FILE} validate"
            }
        }
    }
}