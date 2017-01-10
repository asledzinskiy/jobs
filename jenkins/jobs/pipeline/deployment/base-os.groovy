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
        String templateStr = "{'nodes':" +
                "[{'node1':'null','name':'node1','ip':'${NODE_IPS_ARRAY.getAt(0)}','kube_master':True}," +
                "{'node2':'null','name':'node2','ip':'${NODE_IPS_ARRAY.getAt(1)}','kube_master':True}," +
                "{'node3':'null','name':'node3','ip':'${NODE_IPS_ARRAY.getAt(2)}','kube_master':False}]}"
        sh """
            python -c "import jinja2
from jinja2 import Template
templateLoader=jinja2.FileSystemLoader(searchpath='/')
templateEnv=jinja2.Environment(loader=templateLoader)
TEMPLATE_FILE='${WORKSPACE}/inventory/inventory.cfg'
template=templateEnv.get_template(TEMPLATE_FILE)
templateVars=${templateStr}
outputText=template.render(templateVars)
Template(outputText).stream().dump('${WORKSPACE}/inventory/inventory.cfg')"
        """
    }

    stage("Prepare Operating System") {
        execAnsiblePlaybook('ansible-base-os/preinstall.yml')
    }
}
