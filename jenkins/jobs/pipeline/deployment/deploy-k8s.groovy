gitTools = new com.mirantis.mcp.Git()
common = new com.mirantis.mk.Common()
mcpCommon = new com.mirantis.mcp.Common()
qaCommon = new com.mirantis.mcp_qa.Common()
sshCredentialsId = env.CREDENTIALS ?: 'mcp-ci-k8s-deployment'
def String KARGO_REPO = 'kubernetes/kargo'
def String KARGO_COMMIT = env.KARGO_COMMIT ?: 'master'
def String FUEL_CCP_INSTALLER_COMMIT = env.FUEL_CCP_INSTALLER_COMMIT ?: 'master'
def String WRITE_CONFIG = env.WRITE_CONFIG
def String CLUSTER_NAME=env.CLUSTER_NAME
def String KARGO_TEMPLATE_REPO = env.KARGO_TEMPLATE_REPO ?: 'clusters/kubernetes/' + env.CLUSTER_NAME
def String GERRIT_HOST=env.GERRIT_HOST
def String FUEL_CCP_INSTALLER_REPO = 'ccp/fuel-ccp-installer'
def String NODE_JSON= env.NODE_JSON
def String ANSIBLE_INVENTORY=env.ANSIBLE_INVENTORY
def String INV_SOURCE=env.INV_SOURCE
def String DNS = env.DNS
def String UPSTREAM_DNS = env.UPSTREAM_DNS
def String HYPERKUBE_IMAGE_TAG = env.HYPERKUBE_IMAGE_TAG
def String CALICO_VERSION = env.CALICO_VERSION
def String CALICO_CNI_VERSION = env.CALICO_CNI_VERSION
def String CALICOCTL_IMAGE_TAG = env.CALICOCTL_IMAGE_TAG
def String CALICO_MTU = env.CALICO_MTU
def String SLAVE_NODE_LABEL = env.SLAVE_NODE_LABEL ?: 'deployment'
// validate NODE_JSON if it is in a working JSON format
new groovy.json.JsonSlurperClassic().parseText(NODE_JSON)
NODE_JSON=NODE_JSON.replaceAll('"', '\'')

def execAnsiblePlaybook(String playbookPath,
                        String extra = '') {
    def String CCP_KARGO = 'fuel-ccp-installer/utils/kargo'
    def String ANSIBLE_CONFIG = 'fuel-ccp-installer/utils/kargo/ansible.cfg'

    def String scale_opts = '--forks=50 --timeout=600'

    sh "ln -sf ${WORKSPACE}/kargo/inventory/group_vars ${WORKSPACE}/inventory/group_vars"
    def String extras ="-e @$CCP_KARGO/kargo_default_common.yaml " +
            "-e @$CCP_KARGO/kargo_default_ubuntu.yaml " +
            "-e @inventory/kargo/custom.yaml " +
            "-e host_key_checking=False " +
            extra

    def username = common.getSshCredentials(sshCredentialsId).username
    sshagent (credentials: [sshCredentialsId]) {
        withEnv(["ANSIBLE_CONFIG=${ANSIBLE_CONFIG}"]) {
            sh """
                ansible-playbook \
                --become --become-method=sudo --become-user=root -u ${username} \
                ${scale_opts} ${extras} -i inventory/inventory.cfg ${playbookPath}
            """
        }
    }
}

node("${SLAVE_NODE_LABEL}") {
    if ( Boolean.parseBoolean(WRITE_CONFIG) ) {
        if ( "${NODE_JSON}x" == "x") {
            fail('NODE_JSON must be set')
        }
    }

    stage("Checkout source code") {
        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : "${KARGO_COMMIT}",
            host : "${GERRIT_HOST}",
            project : "${KARGO_REPO}",
            targetDir : "kargo"
        ])

        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : "${FUEL_CCP_INSTALLER_COMMIT}",
            host : "${GERRIT_HOST}",
            project : "${FUEL_CCP_INSTALLER_REPO}",
            targetDir : "fuel-ccp-installer"
        ])

        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : "master",
            host : "${GERRIT_HOST}",
            project : "${KARGO_TEMPLATE_REPO}",
            targetDir : 'inventory'
        ])
        if ( env.FUEL_CCP_INSTALLER_REFS && ! env.FUEL_CCP_INSTALLER_REFS.equals('none') ) {
             def refs = "${FUEL_CCP_INSTALLER_REFS}".split("\n")
             qaCommon.getCustomRefs("https://git.openstack.org", 'openstack/fuel-ccp-installer', "${WORKSPACE}/fuel-ccp-installer", refs)
        }
    }
    if ( Boolean.parseBoolean(env.WRITE_CONFIG) ) {
        stage('Update configs') {
            if (INV_SOURCE == "json") {
                mcpCommon.renderJinjaTemplate(
                   "${NODE_JSON}",
                   "${WORKSPACE}/inventory/inventory.cfg",
                   "${WORKSPACE}/inventory/inventory.cfg"
                )
            } else if (INV_SOURCE == "ansible_inventory") {
                writeFile file: "${WORKSPACE}/inventory/inventory.cfg", text: \
                    "${ANSIBLE_INVENTORY}"
            }
            templateStr = "{'CALICO_CNI_VERSION':'${CALICO_CNI_VERSION}','CALICOCTL_IMAGE_TAG':'${CALICOCTL_IMAGE_TAG}','CALICO_VERSION':'${CALICO_VERSION}'," +
                            "'DNS':'${DNS}','HYPERKUBE_IMAGE_TAG':'${HYPERKUBE_IMAGE_TAG}','UPSTREAM_DNS':'${UPSTREAM_DNS}','CALICO_MTU':'${CALICO_MTU}'}"
            mcpCommon.renderJinjaTemplate(
                "${templateStr}",
                "${WORKSPACE}/inventory/kargo/custom.yaml",
                "${WORKSPACE}/inventory/kargo/custom.yaml"
            )
        }
    }

    stage("Deploying k8s cluster") {
        execAnsiblePlaybook('kargo/cluster.yml')
    }

    stage("Post-install basic verification") {
        execAnsiblePlaybook('fuel-ccp-installer/utils/kargo/postinstall.yml')
    }

    stage("Archive config files") {
        sh "cp inventory/inventory.cfg inventory/kargo/custom.yaml ${WORKSPACE}/"
        archiveArtifacts artifacts: 'inventory.cfg, custom.yaml'
    }

}
