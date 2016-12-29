gitTools = new com.mirantis.mcp.Git()
ssl = new com.mirantis.mk.ssl()
common = new com.mirantis.mk.common()
sshCredentialsId = env.CREDENTIALS ?: 'deployments-key'
def Boolean TEST_MODE = Boolean.parseBoolean(env.TEST_MODE)
def String KARGO_REPO = 'kubernetes/kargo'
def String CLUSTER_NAME=env.CLUSTER_NAME
def String GERRIT_HOST=env.GERRIT_HOST
def String FUEL_CCP_INSTALLER_REPO = 'ccp/fuel-ccp-installer'
def String NODE_IPS= env.NODE_IPS
def String DNS = env.DNS
def String UPSTREAM_DNS = env.UPSTREAM_DNS
def String HYPERKUBE_IMAGE_TAG = env.HYPERKUBE_IMAGE_TAG
def String CALICO_VERSION = env.CALICO_VERSION
def String CALICO_CNI_VERSION = env.CALICO_CNI_VERSION
def String CALICOCTL_IMAGE_TAG = env.CALICOCTL_IMAGE_TAG
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
    def ArrayList NODE_IPS_ARRAY = NODE_IPS.split()
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
            project = "clusters/kubernetes/${CLUSTER_NAME}"
            targetDir = 'inventory'
        }
    }
    stage('Update configs') {
        sh '''
         python -c "import jinja2;from jinja2 import Template;templateLoader=jinja2.FileSystemLoader(searchpath=\"/\");templateEnv=jinja2.Environment(loader=templateLoader);TEMPLATE_FILE=\"/home/ubuntu/inv.cfg\";template=templateEnv.get_template(TEMPLATE_FILE);templateVars={\"nodes\":[{\"node1\":\"null\",\"name\":\"node1\",\"ip\":\"127.0.1.1\",\"kube_master\":True},{\"node2\":\"null\",\"name\":\"node2\",\"ip\":\"127.0.1.2\",\"kube_master\":False},{\"node3\":\"null\",\"name\":\"node3\",\"ip\":\"127.0.1.3\",\"kube_master\":False}]}; outputText=template.render(templateVars);Template(outputText).stream().dump('inventory/inventory.cfg')"
        '''
        sh '''
          python -c "import jinja2;from jinja2 import Template;templateLoader=jinja2.FileSystemLoader(searchpath=\"/\");templateEnv=jinja2.Environment(loader=templateLoader);TEMPLATE_FILE=\"/home/ubuntu/template.j2\";template=templateEnv.get_template(TEMPLATE_FILE);templateVars={\"CALICO_CNI_VERSION\":\"${CALICO_CNI_VERSION}\",\"CALICOCTL_IMAGE_TAG\":\"${CALICOCTL_IMAGE_TAG}\",\"CALICO_VERSION\":\"${CALICO_VERSION}\",\"DNS\":\"${DNS}\",\"HYPERKUBE_IMAGE_TAG\":\"${HYPERKUBE_IMAGE_TAG}\",\"UPSTREAM_DNS\":\"${UPSTREAM_DNS}\"}; outputText=template.render(templateVars);Template(outputText).stream().dump('inventory/kargo/custom.yaml')"
        '''
    }

    stage("Deploying k8s cluster") {
        execAnsiblePlaybook('kargo/cluster.yml')
    }

    stage("Post-install basic verification") {
        execAnsiblePlaybook('fuel-ccp-installer/utils/kargo/postinstall.yml')
    }

}
