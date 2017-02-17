def gitTools = new com.mirantis.mcp.Git()
def String SLAVE_NODE_LABEL = "deployment"
sshCredentialsId = env.CREDENTIALS ?: 'mcp-ci-k8s-deployment'
import groovy.json.JsonOutput

@NonCPS
def parseJsonText(String jsonText) {
  final slurper = new groovy.json.JsonSlurperClassic()
  return new HashMap<>(slurper.parseText(jsonText))
}

node("${SLAVE_NODE_LABEL}") {

  def WORKSPACE = "${env.WORKSPACE}"
  def VENV_DIR = "${WORKSPACE}/venv"
  def STACK_NAME = "${env.STACK_NAME}"

  deleteDir()

  stage('Code checkout') {
    def HOST = env.GERRIT_HOST
    gitTools.gitSSHCheckout ([
      credentialsId : "mcp-ci-gerrit",
      branch : "master",
      host : HOST,
      project : "mcp-ci/mcp-ci-heat-templates",
      targetDir : "heat-templates"
    ])
  }

  stage('Install and configure venv') {
    sh """
      virtualenv --no-site-packages ${VENV_DIR}
      ${VENV_DIR}/bin/pip install -r ${WORKSPACE}/heat-templates/requirements.txt
    """
  }

  stage('Create VM instances') {

    // Prepare SSH pubkeys
    def ssh_jenkins_pubkeys = ''
    sshagent (credentials: [sshCredentialsId]) {
      ssh_jenkins_pubkeys = sh(script: """
        ssh-add -L
      """, returnStdout: true).trim()
    }

    withCredentials([
        [$class: 'UsernamePasswordMultiBinding',
         credentialsId: 'mcp-jenkins',
         passwordVariable: 'JENKINS_PASSWORD',
         usernameVariable: 'JENKINS_LOGIN']
    ]) {
        withEnv([
            "OS_AUTH_URL=${env.OS_AUTH_URL}",
            "OS_IDENTITY_API_VERSION=${env.OS_IDENTITY_API_VERSION}",
            "OS_PROJECT_DOMAIN_NAME=${env.OS_PROJECT_DOMAIN_NAME}",
            "OS_PROJECT_ID=${env.OS_PROJECT_ID}",
            "OS_PROJECT_NAME=${env.OS_PROJECT_NAME}",
            "OS_REGION_NAME=${env.OS_REGION_NAME}",
            "OS_USER_DOMAIN_NAME=${env.OS_USER_DOMAIN_NAME}",
            "OS_USERNAME=${JENKINS_LOGIN}",
            "OS_PASSWORD=${JENKINS_PASSWORD}",
        ]) {
            sh """
              ${VENV_DIR}/bin/openstack stack create \
                -t ${WORKSPACE}/heat-templates/template/k8s_cluster_nodes.yaml \
                --parameter \"lan_network_class=${env.STACK_LAN_NETWORK_CLASS}\" \
                --parameter \"public_float_id=${env.STACK_PUBLIC_FLOAT_ID}\" \
                --parameter \"net_router_id=${env.STACK_NET_ROUTER_ID}\" \
                --parameter \"ssh_jenkins_pubkey='${ssh_jenkins_pubkeys}'\" \
                --parameter \"ssh_user_pubkey='${env.SSH_PUBKEY}'\" \
                --parameter \"nodes_count=${env.CLUSTER_SIZE}\" \
                --wait \
                --timeout 5 \
                ${STACK_NAME}
            """

            // Get IP addresses from created stack and store it for next stages
            def stack_output_string = sh(script: """
              ${VENV_DIR}/bin/openstack stack show \
                -c outputs -f json \
                ${STACK_NAME}
            """, returnStdout: true).trim()
            print stack_output_string
            def stack_output_json = parseJsonText(stack_output_string)
            def nodes_json = stack_output_json.outputs[0].output_value.nodes
            def nodes_output = [:]
            nodes_output['nodes'] = []
            for(int i=0; i<nodes_json.size();i++) {
              def node_num = i + 1
              def node_name = 'node' + node_num
              nodes_output[node_name] = [:]
              nodes_output[node_name]['name'] = nodes_json[i].name
              nodes_output[node_name]['ip'] = nodes_json[i].ip
              nodes_output[node_name]['ip_float'] = nodes_json[i].ip_float
              nodes_output['nodes'] << nodes_output[node_name]
            }
            writeFile file: WORKSPACE + '/node-ips.json', text: JsonOutput.toJson(nodes_output)
        }
    }
  }

  stage('Verify SSH access') {
    sshagent (credentials: [sshCredentialsId]) {
      def node_ips_string = readFile "${WORKSPACE}/node-ips.json"
      def node_ips = parseJsonText(node_ips_string)

      for(i=0; i<node_ips.nodes.size(); i++) {
        def node_ip = node_ips.nodes[i].ip_float
        for(int c=0; c<=30; c++) {
          def test_code = sh script: "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no ubuntu@${node_ip} w", returnStatus: true
          if (c == 30) {
            error("Cannot connect to node ${node_ip} by SSH")
          } else if (test_code != 0) {
            print "Wait for SSH on node ${node_ip} [${c}]"
            sleep 2
          } else {
            break
          }
        }
      }
    }
  }

  stage('Archive artifacts') {
    archiveArtifacts artifacts: 'node-ips.json'
  }

}
