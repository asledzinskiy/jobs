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

            // we need config file from this repo
            gitTools.gitSSHCheckout ([
                    credentialsId : "mcp-ci-gerrit",
                    branch : "master",
                    host : "${GERRIT_HOST}",
                    project : "clusters/mcp/ccp-cicd",
                    targetDir : "ccp-cicd"
            ])
        }

        stage('Prepare config file'){
            writeFile file: CONFIG_FILE, text: """\
                !include
                    - ${WORKSPACE}/ccp-cicd/ccp-config/ccp.yaml
                ---
                repositories:
                  path: ${WORKSPACE}
            """.stripIndent()
        }
        // clone projects as well
        stage('Execute ccp config dump') {
            dir("fuel-ccp"){
                commonTools.runTox "venv -- ccp --config-file ${CONFIG_FILE} config dump"
            }
        }
        // checkout to patchset that should be checked
        dir("${CURRENT_PROJECT}"){
            gitTools.gerritPatchsetCheckout([
                    credentialsId: "mcp-ci-gerrit"
            ])
        }

        stage('Execute ccp validate') {
            dir("fuel-ccp"){
                commonTools.runTox "venv -- ccp --config-file ${CONFIG_FILE} validate"
            }
        }
    }
}