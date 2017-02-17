def gitTools = new com.mirantis.mcp.Git()
def String CLUSTER = env.CLUSTER_NAME
def Boolean DELETE_OLD_ENV = env.DELETE_OLD_ENV
def String KARGO_TEMPLATE_REPO = env.KARGO_TEMPLATE_REPO
def String UNDERLAY = env.UNDERLAY ?: "os-heat"
def String SLAVE_NODE_LABEL = "deployment"
import groovy.json.JsonOutput

@NonCPS
def parseJsonText(String jsonText) {
  final slurper = new groovy.json.JsonSlurperClassic()
  return new HashMap<>(slurper.parseText(jsonText))
}

node("${SLAVE_NODE_LABEL}") {

  def WORKSPACE = "${env.WORKSPACE}"
  def STACK_NAME = "${env.STACK_NAME}"

  def VENV_DIR = "${WORKSPACE}/venv"

  def String KARGO_REPO = 'kubernetes/kargo'
  def String KARGO_COMMIT = env.KARGO_COMMIT ?: 'master'

  def ansible_inventory = ""
  def NODE_JSON = "{\"nodes\":" +
        "[{\"node1\":\"null\",\"name\":\"node1\",\"ip\":\"10.109.0.100\",\"bind_ip\":\"10.109.0.100\",\"kube_master\":\"True\",\"etcd\":\"True\"}," +
        "{\"node2\":\"null\",\"name\":\"node2\",\"ip\":\"10.109.0.101\",\"bind_ip\":\"10.109.0.101\",\"kube_master\":\"True\",\"etcd\":\"True\"}," +
        "{\"node3\":\"null\",\"name\":\"node3\",\"ip\":\"10.109.0.102\",\"bind_ip\":\"10.109.0.102\",\"kube_master\":\"False\",\"etcd\":\"True\"}]}"
  // validate NODE_JSON if it is in a working JSON forma
  new groovy.json.JsonSlurperClassic().parseText(NODE_JSON)

  deleteDir()

  stage('code checkout') {
    def HOST = env.GERRIT_HOST
    gitTools.gitSSHCheckout ([
        credentialsId : "mcp-ci-gerrit",
        branch : "${KARGO_COMMIT}",
        host : "${GERRIT_HOST}",
        project : "${KARGO_REPO}",
        targetDir : "kargo"
    ])
    gitTools.gitSSHCheckout ([
      credentialsId : "mcp-ci-gerrit",
      branch : "master",
      host : HOST,
      project : "mcp-ci/mcp-ci-heat-templates",
      targetDir : "heat-templates"
    ])
  }

  try {
    stage('Install and configure venv') {
      sh """
        virtualenv --no-site-packages ${VENV_DIR}
        ${VENV_DIR}/bin/pip install -r ${WORKSPACE}/heat-templates/requirements.txt
      """
    }

    stage("Delete old cluster ${CLUSTER}") {
        if ( Boolean.parseBoolean(env.DELETE_OLD_ENV) ) {
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
                    // Required better solution, now it will not catch the errors
                    sh """
                      if ${VENV_DIR}/bin/openstack stack show ${STACK_NAME}; then
                          ${VENV_DIR}/bin/openstack stack delete -y --wait ${STACK_NAME}
                      fi
                    """
                }
            }
        }
    }

    stage("Create stack for ${CLUSTER}") {
        def build_result = build job: "${CLUSTER}-create-${UNDERLAY}-env",
            parameters: [
              string(name: 'SLAVE_NODE_LABEL', value: "${SLAVE_NODE_LABEL}"),
              string(name: 'SSH_PUBKEY', value: "${env.SSH_PUBKEY}"),
              string(name: 'STACK_NAME', value: "${STACK_NAME}"),
              string(name: 'CLUSTER_SIZE', value: "${env.CLUSTER_SIZE}"),
            ]
        step([$class: 'CopyArtifact',
                filter: 'node-ips.json',
                fingerprintArtifacts: true,
                projectName: "${CLUSTER}-create-${UNDERLAY}-env",
                selector: [$class: 'SpecificBuildSelector',
                                buildNumber: build_result.getNumber().toString()
                ]
        ])
        sh 'echo node ips; cat node-ips.json'
        def node_ips_string = readFile "${WORKSPACE}/node-ips.json"
        def node_ips = parseJsonText(node_ips_string)

        def node_json = parseJsonText(NODE_JSON)
        for(int i=0; i<node_json.nodes.size();i++) {
          def node_ip_float = node_ips['nodes'][i].ip_float
          def node_ip = node_ips['nodes'][i].ip
          node_json.nodes[i].ip = node_ip_float
          node_json.nodes[i].bind_ip = node_ip
        }
        NODE_JSON = groovy.json.JsonOutput.toJson(node_json)
    }

    stage("Prepare nodes for k8s installation") {
        build job: "${CLUSTER}-configure-system", propagate: true, wait: true,
            parameters: [
                      string(name: 'SLAVE_NODE_LABEL', value: "${SLAVE_NODE_LABEL}"),
                      string(name: 'NODE_JSON', value: "${NODE_JSON}"),
                      string(name: 'INV_SOURCE', value: "json"),
                      string(name: 'KARGO_TEMPLATE_REPO', value: "${KARGO_TEMPLATE_REPO}"),
                      booleanParam(name: 'TEST_MODE', value: false)
            ]
    }

    stage("Execute k8s installation") {
        def build_result = build job: "${CLUSTER}-deploy-k8s", propagate: true, wait: true,
            parameters: [
                      string(name: 'KARGO_COMMIT', value: "${KARGO_COMMIT}"),
                      string(name: 'KARGO_TEMPLATE_REPO', value: "${KARGO_TEMPLATE_REPO}"),
                      string(name: 'SLAVE_NODE_LABEL', value: "${SLAVE_NODE_LABEL}"),
                      string(name: 'NODE_JSON', value: "${NODE_JSON}"),
                      string(name: 'INV_SOURCE', value: "json")
            ]
        step([$class: 'CopyArtifact',
                filter: 'inventory.cfg, custom.yaml',
                fingerprintArtifacts: true,
                projectName: "${CLUSTER}-deploy-k8s",
                selector: [$class: 'SpecificBuildSelector',
                                buildNumber: build_result.getNumber().toString()
                ]
        ])

    }

    stage('Prepare and archive artifacts') {
        def node_json = parseJsonText(NODE_JSON)
        writeFile file: WORKSPACE + '/k8s-ip.txt', text: node_json.nodes[0].ip
        writeFile file: WORKSPACE + '/k8s-nodes.json', text: NODE_JSON
        archiveArtifacts artifacts: 'k8s-nodes.json, k8s-ip.txt, inventory.cfg, custom.yaml'
    }

  } catch (InterruptedException x) {
      echo "The job was aborted"
      echo x.getMessage()
      currentBuild.result = 'FAILURE'
  } catch (err) {
      echo "The job was aborted"
      echo err.getMessage()
      currentBuild.result = 'FAILURE'
  }

}
