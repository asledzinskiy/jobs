gitTools = new com.mirantis.mcp.Git()
ssl = new com.mirantis.mk.ssl()
common = new com.mirantis.mk.common()
mcpCommon = new com.mirantis.mcp.Common()
sshCredentialsId = env.CREDENTIALS ?: 'mcp-ci-k8s-deployment'
def String KARGO_REPO = 'kubernetes/kargo'
def String FUEL_CCP_INSTALLER_REPO = 'ccp/fuel-ccp-installer'
def String ANSIBLE_K8S_BASE_REPO = 'mcp-ci/ansible-k8s-base'
def String CLUSTER_NAME=env.CLUSTER_NAME
def String GERRIT_HOST=env.GERRIT_HOST
def String INV_SOURCE=env.INV_SOURCE ?: ''
def String NODE_JSON=env.NODE_JSON
def String ANSIBLE_INVENTORY=env.ANSIBLE_INVENTORY ?: ""
def String SLAVE_NODE_LABEL = env.SLAVE_NODE_LABEL ?: 'deployment'
// validate NODE_JSON if it is in a working JSON format
new groovy.json.JsonSlurperClassic().parseText(NODE_JSON)
NODE_JSON=NODE_JSON.replaceAll('"', '\'')

def execAnsiblePlaybook(String playbookPath,
                        String extra = '') {

    def String extras = "-e host_key_checking=False ${extra}"

    ssl.prepareSshAgentKey(sshCredentialsId)
    def username = common.getSshCredentials(sshCredentialsId).username

    if ( Boolean.parseBoolean(env.TEST_MODE) ) {
        // then let's create deployment user
        writeFile file: 'deployment_user.yaml', text: """\
          k8s_deployment_user: ${username}
          k8s_deployment_user_pkey_path: /home/jenkins/.ssh/id_rsa_${sshCredentialsId}
        """.stripIndent()

        withEnv(["ANSIBLE_CONFIG=kargo/ansible.cfg"]) {
            sh """
                ansible-playbook --become --become-method=sudo \
                --become-user=root --extra-vars 'ansible_ssh_pass=vagrant' \
                --ssh-common-args='-o UserKnownHostsFile=/dev/null' \
                -u vagrant ${extras} -e @deployment_user.yaml \
                -i inventory/inventory.cfg ${playbookPath}
            """
        }
    } else {
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
    if ( "${NODE_JSON}x" == "x") {
        fail('NODE_JSON must be set')
    }

    stage("Checkout source code") {
        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : "master",
            host : "${GERRIT_HOST}",
            project : "${KARGO_REPO}",
            targetDir : "kargo"
        ])

        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : "master",
            host : "${GERRIT_HOST}",
            project : "${FUEL_CCP_INSTALLER_REPO}",
            targetDir : "fuel-ccp-installer",
        ])

        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : "master",
            host : "${GERRIT_HOST}",
            project : "${ANSIBLE_K8S_BASE_REPO}",
            targetDir : 'ansible-base-os'
        ])

        gitTools.gitSSHCheckout ([
            credentialsId : "mcp-ci-gerrit",
            branch : "master",
            host : "${GERRIT_HOST}",
            project : "clusters/kubernetes/${CLUSTER_NAME}",
            targetDir : 'inventory'
        ])
    }

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
    }

    stage("Prepare Operating System") {
        execAnsiblePlaybook('ansible-base-os/preinstall.yml')
    }
}
