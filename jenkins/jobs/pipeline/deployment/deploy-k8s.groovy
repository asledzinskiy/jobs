gitTools = new com.mirantis.mcp.Git()
ssl = new com.mirantis.mk.ssl()
common = new com.mirantis.mk.common()
sshCredentialsId = 'deployments-key'
def String KARGO_REPO = 'kubernetes/kargo'
def String CLUSTER_NAME=env.CLUSTER_NAME
def String GERRIT_HOST=env.GERRIT_HOST
def String FUEL_CCP_INSTALLER_REPO = 'ccp/fuel-ccp-installer'

def execAnsiblePlaybook(String playbookPath,
                        String extra = '') {
    def String CCP_KARGO = 'fuel-ccp-installer/utils/kargo'

    def String extras = "-e @kargo/inventory/group_vars/all.yml " +
            "-e @$CCP_KARGO/kargo_default_common.yaml " +
            "-e @$CCP_KARGO/kargo_default_ubuntu.yaml " +
            "-e @inventory/kargo/custom.yaml " +
            "-e host_key_checking=False " +
            extra

    // FIXME: don't print private key
    ssl.prepareSshAgentKey(sshCredentialsId)
    username = common.getSshCredentials(sshCredentialsId).username
    withEnv(["ANSIBLE_CONFIG=kargo/ansible.cfg"]) {
        sh """
            ansible-playbook --private-key=~/.ssh/id_rsa_${sshCredentialsId} \
            --become --become-method=sudo --become-user=root -u ${username} \
            ${extras} -i inventory/inventory.cfg ${playbookPath}
        """
    }
}

node("deployment") {
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

    stage("Deploying k8s cluster") {
        execAnsiblePlaybook('kargo/cluster.yml')
    }

    stage("Post-install basic verification") {
        execAnsiblePlaybook('fuel-ccp-installer/utils/kargo/postinstall.yml')
    }

}
