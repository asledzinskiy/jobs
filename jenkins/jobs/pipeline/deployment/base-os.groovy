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
def String NODE_JSON=env.NODE_JSON
def String SLAVE_NODE_LABEL = env.SLAVE_NODE_LABEL ?: 'deployment'
// validate NODE_JSON if it is in a working JSON format
new groovy.json.JsonSlurperClassic().parseText(NODE_JSON)
NODE_JSON=NODE_JSON.replaceAll('"', '\'')

def execAnsiblePlaybook(String playbookPath,
                        String extra = '') {
   def String extras = "-e host_key_checking=False ${extra}"

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
    if ( "${NODE_JSON}x" == "x") {
        fail('NODE_JSON must be set')
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
        sh """
            python -c "import jinja2
from jinja2 import Template
templateLoader=jinja2.FileSystemLoader(searchpath='/')
templateEnv=jinja2.Environment(loader=templateLoader)
TEMPLATE_FILE='${WORKSPACE}/inventory/inventory.cfg'
template=templateEnv.get_template(TEMPLATE_FILE)
templateVars=${NODE_JSON}
outputText=template.render(templateVars)
Template(outputText).stream().dump('${WORKSPACE}/inventory/inventory.cfg')"
        """
    }

    stage("Prepare Operating System") {
        execAnsiblePlaybook('ansible-base-os/preinstall.yml')
    }
}
