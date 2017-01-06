gitTools = new com.mirantis.mcp.Git()
ssl = new com.mirantis.mk.ssl()
common = new com.mirantis.mk.common()
sshCredentialsId = 'deployments-key'
def Boolean TEST_MODE = Boolean.parseBoolean(env.TEST_MODE)
def String KARGO_REPO = 'kubernetes/kargo'
def String FUEL_CCP_INSTALLER_REPO = 'ccp/fuel-ccp-installer'
def String ANSIBLE_K8S_BASE_REPO = 'mcp-ci/ansible-k8s-base'
def String CLUSTER_NAME=env.CLUSTER_NAME
def String GERRIT_HOST=env.GERRIT_HOST
def String NODE_IPS=env.NODE_IPS
def String SLAVE_NODE_LABEL = env.SLAVE_NODE_LABEL ?: 'deployment'


def execAnsiblePlaybook(String playbookPath,
                        String extra = '') {
    def String CCP_KARGO = 'fuel-ccp-installer/utils/kargo'

    def String extras = "-e @kargo/inventory/group_vars/all.yml " +
            "-e @$CCP_KARGO/kargo_default_common.yaml " +
            "-e @$CCP_KARGO/kargo_default_ubuntu.yaml " +
            "-e @inventory/kargo/custom.yaml " +
            "-e host_key_checking=False " +
            extra

    if ( TEST_MODE ) {
        def username = "vagrant"
        def password = "vagrant"
        withEnv(["ANSIBLE_CONFIG=kargo/ansible.cfg"]) {
            sh """
                ansible-playbook --become --become-method=sudo \
                --become-user=root --extra-vars 'ansible_ssh_pass=${password}' \
                -u ${username} ${extras} -i inventory/inventory.cfg ${playbookPath}
            """
        }
    } else {
        ssl.prepareSshAgentKey(sshCredentialsId)
        def username = common.getSshCredentials(sshCredentialsId).username
        withEnv(["ANSIBLE_CONFIG=kargo/ansible.cfg"]) {
            sh """
                ansible-playbook --private-key=~/.ssh/id_rsa_${sshCredentialsId} \
                --become --become-method=sudo --become-user=root -u ${username} \
                ${extras} -i inventory/inventory.cfg ${playbookPath}
            """
        }
    }
}

node("${SLAVE_NODE_LABEL}") {
    if ( "${NODE_IPS}x" == "x") {
        fail('NODE_IPS must be set')
    }
    def ArrayList NODE_IPS_ARRAY = new ArrayList(Arrays.asList(NODE_IPS.split()))
    if ( NODE_IPS_ARRAY.size() != 3) {
        // FIXME: Remove this once config template become universal
        fail('Count of NODE_IPS should be equal to 3')
    }

    stage("Checkout source code") {
        gitTools.gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = "${GERRIT_HOST}"
            project = "${KARGO_REPO}"
            targetDir = "kargo"
        }

        gitTools.gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = "${GERRIT_HOST}"
            project = "${FUEL_CCP_INSTALLER_REPO}"
            targetDir = "fuel-ccp-installer"
        }

        gitTools.gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = "${GERRIT_HOST}"
            project = "${ANSIBLE_K8S_BASE_REPO}"
            targetDir = 'ansible-base-os'
        }

        gitTools.gitSSHCheckout {
            credentialsId = "mcp-ci-gerrit"
            branch = "master"
            host = "${GERRIT_HOST}"
            project = "clusters/kubernetes/${CLUSTER_NAME}"
            targetDir = 'inventory'
        }
    }

    stage('Update configs') {
        // FIXME: Rewrite to jinja
        for( int i = 0; i < NODE_IPS_ARRAY.size(); i++) {
            ip = NODE_IPS_ARRAY.get(i)
            sh "sed -i 's/SLAVE${i}_IP/${ip}/g' inventory/inventory.cfg"
        }
    }

    stage("Prepare Operating System") {
        execAnsiblePlaybook('ansible-base-os/preinstall.yml')
    }
}
